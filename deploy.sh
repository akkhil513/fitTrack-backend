#!/usr/bin/env bash
#
# FitTrack backend: test, build, upload, update the Lambda and deploy the stack.
#
#   ./deploy.sh                  deploy to dev (asks before applying infrastructure changes)
#   ./deploy.sh --dry-run        build and preview the CloudFormation change set; change nothing
#   ./deploy.sh --env prod       deploy to prod
#   ./deploy.sh --stack NAME     use a different stack name (default: fittrack-backend)
#   ./deploy.sh --yes            do not ask for confirmation (removals/replacements still abort)
#   ./deploy.sh --skip-tests     skip ./mvnw test (not recommended: the authorization tests live there)
#   ./deploy.sh --allow-replace  let the change set replace or remove resources (read the preview first)
#
# Needs: AWS CLI configured for the account that owns the stack, Java 17, curl.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# ---- settings (override with environment variables or flags) ----------------
ENVIRONMENT="${ENVIRONMENT:-dev}"
STACK_NAME="${STACK_NAME:-fittrack-stack}"
BUCKET="${BUCKET:-fittrack-deployments}"
S3_KEY="${S3_KEY:-backend/function.zip}"
TEMPLATE_KEY="${TEMPLATE_KEY:-backend/template.yaml}"
COGNITO_POOL_ID="${COGNITO_POOL_ID:-us-east-1_FiMxzIAMO}"
COGNITO_CLIENT_ID="${COGNITO_CLIENT_ID:-5mqksvmvu33fqsk9e570f22jcf}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"

DRY_RUN=false
ASSUME_YES=false
SKIP_TESTS=false
ALLOW_REPLACE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)           ENVIRONMENT="${2:?--env needs dev or prod}"; shift 2 ;;
    --stack)         STACK_NAME="${2:?--stack needs a name}"; shift 2 ;;
    --dry-run)       DRY_RUN=true; shift ;;
    --yes|-y)        ASSUME_YES=true; shift ;;
    --skip-tests)    SKIP_TESTS=true; shift ;;
    --allow-replace) ALLOW_REPLACE=true; shift ;;
    -h|--help)       sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
  esac
done

[[ "$ENVIRONMENT" == "dev" || "$ENVIRONMENT" == "prod" ]] || { echo "--env must be dev or prod" >&2; exit 2; }
FUNCTION_NAME="fittrack-backend-lambda-${ENVIRONMENT}"

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[33mWARN: %s\033[0m\n' "$*" >&2; }
die()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

confirm() {
  $ASSUME_YES && return 0
  read -r -p "$1 [y/N] " reply
  [[ "$reply" == "y" || "$reply" == "Y" ]]
}

CHANGE_SET=""
CS_TYPE=UPDATE
cleanup() {
  # An unexecuted change set must not linger; a never-created stack left in REVIEW_IN_PROGRESS is removed too.
  if [[ -n "$CHANGE_SET" ]]; then
    aws cloudformation delete-change-set --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET" >/dev/null 2>&1 || true
    if [[ "$CS_TYPE" == "CREATE" ]]; then
      aws cloudformation delete-stack --stack-name "$STACK_NAME" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup EXIT

# ---- 1. preflight ------------------------------------------------------------
say "Preflight"
command -v aws  >/dev/null || die "AWS CLI not found"
command -v curl >/dev/null || die "curl not found"
[[ -x ./mvnw ]] || die "./mvnw not found or not executable; run from the backend folder"
[[ -f template.yaml ]] || die "template.yaml not found"

IDENTITY="$(aws sts get-caller-identity --query '[Account,Arn]' --output text)" \
  || die "AWS credentials are not working (try: aws sts get-caller-identity)"
echo "AWS identity : $IDENTITY"
echo "Region       : $AWS_DEFAULT_REGION"
echo "Stack        : $STACK_NAME   Environment: $ENVIRONMENT   Function: $FUNCTION_NAME"
$DRY_RUN && echo "Mode         : DRY RUN (nothing will be changed)"

aws s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1 || die "S3 bucket '$BUCKET' not reachable"

STACK_STATUS="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || true)"
echo "Stack status : ${STACK_STATUS:-NOT FOUND}"
STACK_EXISTS=true
case "$STACK_STATUS" in ""|None|DELETE_COMPLETE|REVIEW_IN_PROGRESS) STACK_EXISTS=false ;; esac
if ! $STACK_EXISTS; then
  warn "Stack '$STACK_NAME' does not exist; this would CREATE it. If you expected an existing stack, check --stack and the region."
  $DRY_RUN || confirm "Continue and create a new stack?" || die "Aborted"
  CS_TYPE=CREATE
fi

# The app refreshes sessions with a refresh token; without this flow users are signed out after about an hour.
FLOWS="$(aws cognito-idp describe-user-pool-client --user-pool-id "$COGNITO_POOL_ID" --client-id "$COGNITO_CLIENT_ID" \
  --query 'UserPoolClient.ExplicitAuthFlows' --output text 2>/dev/null || true)"
if [[ -z "$FLOWS" ]]; then
  warn "Could not read the Cognito app client settings (permissions?). Verify ALLOW_REFRESH_TOKEN_AUTH is enabled."
elif [[ "$FLOWS" != *ALLOW_REFRESH_TOKEN_AUTH* ]]; then
  warn "Cognito app client is missing ALLOW_REFRESH_TOKEN_AUTH: users will be signed out when the ID token expires."
fi

# ---- 2. tests ----------------------------------------------------------------
if $SKIP_TESTS; then
  warn "Skipping tests"
else
  say "Running tests"
  ./mvnw -q test
fi

# ---- 3. build ----------------------------------------------------------------
say "Building function.zip"
./mvnw -q clean package -DskipTests
[[ -s target/function.zip ]] || die "target/function.zip was not produced"
ls -lh target/function.zip

if $DRY_RUN; then
  say "Dry run: skipping upload and Lambda update"
else
  # ---- 4. upload + update function code --------------------------------------
  say "Uploading to s3://$BUCKET/$S3_KEY"
  aws s3 cp target/function.zip "s3://$BUCKET/$S3_KEY" --only-show-errors

  # The template points at a fixed S3 key, so CloudFormation cannot see new bytes at that key.
  # Point the function at the uploaded code explicitly.
  if aws lambda get-function --function-name "$FUNCTION_NAME" >/dev/null 2>&1; then
    say "Updating Lambda code: $FUNCTION_NAME"
    aws lambda update-function-code --function-name "$FUNCTION_NAME" \
      --s3-bucket "$BUCKET" --s3-key "$S3_KEY" --query 'LastModified' --output text
    aws lambda wait function-updated --function-name "$FUNCTION_NAME"
  else
    warn "Lambda '$FUNCTION_NAME' does not exist yet; the stack deploy below will create it from the uploaded zip."
  fi
fi

# ---- 5. infrastructure change set -------------------------------------------
say "Previewing infrastructure changes"
CHANGE_SET="fittrack-$(date +%Y%m%d%H%M%S)"

# create-change-set accepts at most 51,200 bytes inline; larger templates go through S3.
if [[ "$(wc -c < template.yaml)" -gt 51200 ]]; then
  aws s3 cp template.yaml "s3://$BUCKET/$TEMPLATE_KEY" --only-show-errors
  TEMPLATE_ARG=(--template-url "https://$BUCKET.s3.$AWS_DEFAULT_REGION.amazonaws.com/$TEMPLATE_KEY")
else
  TEMPLATE_ARG=(--template-body "file://template.yaml")
fi

aws cloudformation create-change-set \
  --stack-name "$STACK_NAME" \
  --change-set-name "$CHANGE_SET" \
  --change-set-type "$CS_TYPE" \
  "${TEMPLATE_ARG[@]}" \
  --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND \
  --parameters \
      "ParameterKey=Environment,ParameterValue=$ENVIRONMENT" \
      "ParameterKey=CognitoUserPoolId,ParameterValue=$COGNITO_POOL_ID" \
      "ParameterKey=CognitoClientId,ParameterValue=$COGNITO_CLIENT_ID" >/dev/null

HAS_CHANGES=true
if ! aws cloudformation wait change-set-create-complete --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET" 2>/dev/null; then
  REASON="$(aws cloudformation describe-change-set --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET" \
    --query 'StatusReason' --output text)"
  if [[ "$REASON" == *"didn't contain changes"* || "$REASON" == *"No updates"* ]]; then
    echo "No infrastructure changes."
    HAS_CHANGES=false
  else
    die "Change set failed: $REASON"
  fi
fi

if $HAS_CHANGES; then
  aws cloudformation describe-change-set --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET" \
    --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType,Replacement]' --output table

  RISKY="$(aws cloudformation describe-change-set --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET" \
    --query "Changes[?ResourceChange.Action=='Remove' || ResourceChange.Replacement=='True'].ResourceChange.[Action,LogicalResourceId,ResourceType]" \
    --output text)"
  if [[ -n "$RISKY" ]]; then
    printf '\033[31mThese resources would be REMOVED or REPLACED:\033[0m\n%s\n' "$RISKY"
    $ALLOW_REPLACE || die "Refusing to continue. Review the list (tables and user pools hold data). Re-run with --allow-replace only if intended."
    warn "--allow-replace given; continuing"
  fi

  if $DRY_RUN; then
    say "Dry run complete: the change set shown above was not applied"
    exit 0
  fi

  confirm "Apply these infrastructure changes to '$STACK_NAME'?" || die "Aborted before applying changes"
  say "Applying changes"
  aws cloudformation execute-change-set --stack-name "$STACK_NAME" --change-set-name "$CHANGE_SET"
  CHANGE_SET=""   # an executed change set must not be deleted by cleanup
  if [[ "$CS_TYPE" == "CREATE" ]]; then
    aws cloudformation wait stack-create-complete --stack-name "$STACK_NAME"
  else
    aws cloudformation wait stack-update-complete --stack-name "$STACK_NAME"
  fi
else
  $DRY_RUN && { say "Dry run complete"; exit 0; }
fi

# ---- 6. smoke tests ----------------------------------------------------------
say "Smoke tests"
API="$(aws cloudformation describe-stacks --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" --output text)"
[[ -n "$API" && "$API" != "None" ]] || die "Could not read ApiEndpoint from the stack outputs"
API="${API%/}"
echo "API: $API"

FAILED=0
check() {  # check <description> <expected status> <url>
  local code
  code="$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$3" || true)"
  if [[ "$code" == "$2" ]]; then
    printf '  PASS  %s (%s)\n' "$1" "$code"
  else
    printf '  FAIL  %s (expected %s, got %s)\n' "$1" "$2" "${code:-000}"
    FAILED=1
  fi
}
check "GET /health is public" 200 "$API/health"
if [[ $FAILED -ne 0 ]]; then   # a cold start can be slow; retry once
  sleep 5; FAILED=0
  check "GET /health is public (retry)" 200 "$API/health"
fi
check "protected route rejects a missing token" 401 "$API/plans/deploy-smoke-test"

[[ $FAILED -eq 0 ]] || die "Smoke tests failed. Check CloudWatch logs: /aws/lambda/$FUNCTION_NAME"

say "Deployed OK"
cat <<EOF
Next:
  - Open the app, sign in, and load the Today tab.
  - Weekly plans run daily at 2 AM Eastern and build after your last training day:
    aws logs tail /aws/lambda/$FUNCTION_NAME --since 1d | grep -i weekly
EOF
