#!/bin/bash
set -e

BUILD_TYPE="qa"
if [[ "$TRAVIS_BRANCH" == "master" ]]; then BUILD_TYPE="prod" ; fi

APK_FILENAME=$(./gradlew -q getApkFilename)
echo "Building $APK_FILENAME -- branch: $TRAVIS_BRANCH"
echo "BUILD_TYPE: $BUILD_TYPE"

if [[ -z "$BUILD_TYPE" ]]; then
  echo "Invalid Build Type " $BUILD_TYPE ". Expecting 'qa' or 'prod'"
  exit 1
fi

S3_PATH="s3://android.truex.com/mobile/$BUILD_TYPE/builds/referenceapp"
if [ "$(aws s3 ls $S3_PATH/$APK_FILENAME | grep -c $APK_FILENAME)" != "0" ]; then
  echo "Error: $APK_FILENAME already exists on AWS: $S3_PATH"
  echo "Update App Version. Terminating Build/Deployment..."
  exit 1
elif [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "Excluding Pull Requests from build deployment"
else
  echo "Starting gradle build and deployment to $S3_PATH"
  ./gradlew clean build -Pbuild_type=$BUILD_TYPE
  aws s3 cp ReferenceApp/build/outputs/apk/phone/debug/$APK_FILENAME $S3_PATH/$APK_FILENAME || exit 1
fi

exit 0
