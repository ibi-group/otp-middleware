export BRANCH=$(
  if [ -n "$GITHUB_HEAD_REF" ]; then
    echo $GITHUB_HEAD_REF;
  else
    echo $GITHUB_REF;
  fi
)
export BRANCH_CLEAN=${BRANCH//\//_}
# `git describe --tags --always HEAD` returns a string of the form v0.0.0-52-ge10d02d.
# It assumes you have pushed a tag on a commit on github (e.g. a commit on the dev branch).
# If for some reason git can't find a tag, fallback with --always to a commit sha.
export JAR_VERSION=$(git describe --tags --always HEAD)
echo $JAR_VERSION
# Create a deployment folder, and a folder for the branch.
mkdir deploy
mkdir deploy/$BRANCH_CLEAN
# Add a file with latest build info (that continuous deployment scripts can probe).
echo otp-middleware-$JAR_VERSION.jar > deploy/$BRANCH_CLEAN/latest.txt
# Add the JAR file.
cp target/otp-middleware.jar "deploy/$BRANCH_CLEAN/otp-middleware-$JAR_VERSION.jar"