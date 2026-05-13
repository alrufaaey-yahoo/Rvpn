#!/bin/bash

set -e

source buildScript/init/version.sh

DIR="app/src/main/assets/sing-box"
GENERATER="libcore/cmd/ruleset_generate"
rm -rf $DIR || true
mkdir -p $DIR

get_latest_release() {
  curl --silent "https://api.github.com/repos/$1/releases/latest" | \
    grep '"tag_name":' | \
    sed -E 's/.*"([^"]+)".*/\1/'
}

# Check if the specified versions exist, if not, get the latest
check_url() {
    curl --output /dev/null --silent --head --fail "$1"
}

GEOIP_URL="https://github.com/Dreamacro/maxmind-geoip/releases/download/$GEOIP_VERSION/Country.mmdb"
GEOSITE_URL="https://github.com/v2fly/domain-list-community/releases/download/$GEOSITE_VERSION/dlc.dat"

if ! check_url "$GEOIP_URL"; then
    echo "GEOIP version $GEOIP_VERSION not found, fetching latest..."
    GEOIP_VERSION=$(get_latest_release "Dreamacro/maxmind-geoip")
fi

if ! check_url "$GEOSITE_URL"; then
    echo "GEOSITE version $GEOSITE_VERSION not found, fetching latest..."
    GEOSITE_VERSION=$(get_latest_release "v2fly/domain-list-community")
fi

echo "Using GEOIP: $GEOIP_VERSION"
echo "Using GEOSITE: $GEOSITE_VERSION"

pushd $GENERATER
go run . -geoip=$GEOIP_VERSION -geosite=$GEOSITE_VERSION -so="geosite.tgz" -io="geoip.tgz"
popd

cp -r "$GENERATER/geoip.tgz" "$DIR"
cp -r "$GENERATER/geosite.tgz" "$DIR"
sha256sum $DIR/*.tgz

cd $DIR
echo -n $GEOIP_VERSION >geoip.version.txt
echo -n $GEOSITE_VERSION >geosite.version.txt
