#!/bin/bash
set -e
BUILD_TYPE=$1
APK_FILENAME=$(./gradlew -q getApkFilename)
echo "Building Android Mobile Reference App -- branch: $TRAVIS_BRANCH"
echo "BUILD_TYPE: $BUILD_TYPE"

if [[ -z "$BUILD_TYPE" ]]; then
  echo "Invalid Build Type " $BUILD_TYPE ". Expecting 'qa' or 'prod'"
  exit 1
fi

FILEPATH="s3://android.truex.com/mobile/$BUILD_TYPE/builds/referenceapp"
if [ "$(aws s3 ls $FILEPATH --recursive | grep -c $APK_FILENAME)" != "0" ]; then
  echo "Error: $APK_FILENAME already exists on AWS: $FILEPATH.  Update App Version.  Terminating Build/Deployment..."
  exit 1
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Excluding Pull Requests from build deployment"
else
  echo "Starting gradle build and deployment to $FILEPATH"
  # Deploy taken care of by Travis
  ./gradlew clean build -Pbuild_type=$BUILD_TYPE
fi

exit 0
