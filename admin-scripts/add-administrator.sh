#!/bin/bash

set -euo pipefail

if [ $# -ne 3 ]
  then
    echo "Usage: $0 region environment private-key-path"
    exit 1
fi

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

REGION=$1
ENVIRONMENT=$2
PRIVATE_KEY_PATH=$3

case $ENVIRONMENT in
  prod)
    STACK_SUFFIX=
    ;;
  *)
    STACK_SUFFIX=-$ENVIRONMENT
    ;;
esac

PUBLIC_KEY=$(
  openssl rsa \
  -in $PRIVATE_KEY_PATH \
  -pubout \
  -outform DER 2> /dev/null |
  xxd -plain |
  tr -d '[:space:]'
)

RDS_HOSTNAME=$(
  aws cloudformation describe-stacks \
    --region $REGION \
    --stack-name liquidity-infrastructure$STACK_SUFFIX \
    --output text \
    --query \
      "Stacks[?StackName=='liquidity-infrastructure$STACK_SUFFIX'] \
      | [0].Outputs[?OutputKey=='RDSHostname'].OutputValue"
)
RDS_USERNAME=$(
  aws cloudformation describe-stacks \
    --region $REGION \
    --stack-name liquidity-infrastructure$STACK_SUFFIX \
    --output text \
    --query \
      "Stacks[?StackName=='liquidity-infrastructure$STACK_SUFFIX'] \
      | [0].Outputs[?OutputKey=='RDSUsername'].OutputValue"
)
RDS_PASSWORD=$(
  aws cloudformation describe-stacks \
    --region $REGION \
    --stack-name liquidity-infrastructure$STACK_SUFFIX \
    --output text \
    --query \
      "Stacks[?StackName=='liquidity-infrastructure$STACK_SUFFIX'] \
      | [0].Outputs[?OutputKey=='RDSPassword'].OutputValue"
)

docker run \
  --rm \
  --volume $DIR/rds-combined-ca-bundle.pem:/rds-combined-ca-bundle.pem \
  mysql:5 \
  mysql \
    --ssl-ca=/rds-combined-ca-bundle.pem \
    --ssl-mode=VERIFY_IDENTITY \
    --host=$RDS_HOSTNAME \
    --user=$RDS_USERNAME \
    --password=$RDS_PASSWORD \
    liquidity_administrators -e " \
      INSERT INTO administrators (public_key) \
        VALUES (x'$PUBLIC_KEY') \
    "