#!/bin/bash
#
# Copyright (C) 2009-2013 FoundationDB, LLC
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

set -e

PACKAGING_DIR=$(cd $(dirname "${0}") ; echo "${PWD}")
TOP_DIR=$(cd "${PACKAGING_DIR}/.." ; echo "${PWD}")
STAGE_DIR="${TOP_DIR}/target/packaging"

GIT_HASH=`git rev-parse --short HEAD`
GIT_COUNT=`git rev-list --merges HEAD |wc -l |tr -d ' '` # --count is newer

# $1 - output bin dir
# $2 - output jar dir
build_sql_layer() {
    if [ "$1" = "" -o "$2" = "" ]; then
        echo "Missing argument" >&2
        exit 1
    fi

    LAYER_MVN_VERSION=$(cd "${TOP_DIR}" ; mvn -o org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version |grep '^[0-9]')
    LAYER_VERSION=${LAYER_MVN_VERSION%-SNAPSHOT}
    
    echo "Building FoundationDB SQL Layer ${LAYER_VERSION}"
    pushd .
    cd "${TOP_DIR}"
    mvn clean package -q -B -U -DGIT_COUNT=${GIT_COUNT} -DGIT_HASH=${GIT_HASH} -DskipTests=true
    mkdir -p "${1}" "${2}"/{server,plugins}
    cp "${TOP_DIR}/bin/fdbsqllayer" "${1}/"
    cp target/fdb-sql-layer-${LAYER_MVN_VERSION}.jar "${2}/"
    cp target/dependency/* "${2}/server/"
    popd
}

# $1 - output bin dir
# $2 - output jar dir
build_client_tools() {
    : ${TOOLS_LOC:="git@github.com:FoundationDB/sql-layer-client-tools.git"}
    
    echo "Building client-tools: ${TOOLS_LOC}"
    pushd .
    cd "${TOP_DIR}/target"
    rm -rf client-tools
    git clone -q "${TOOLS_LOC}" client-tools
    cd client-tools
    mvn clean package -q -B -U -DskipTests=true
    rm -f target/*-tests.jar target/*-sources.jar
    mkdir -p "${1}" "${2}/client"
    cp bin/fdbsql{dump,load} "${1}/"
    cp target/fdb-sql-layer-client-tools-*.jar "${2}/"
    cp target/dependency/* "${2}/client/"
    popd
}

# $1 = output conf dir
# $2 = install data dir
# $3 = install log dir
# $4 = install temp dir
filter_config_files() {
    if [ "$1" = "" -o "$2" = "" -o "$3" = "" -o "$4" = "" ]; then
        echo "Missing argument" >&2
        exit 1
    fi
    mkdir -p "${1}"
    pushd .
    cd "${PACKAGING_DIR}/conf"
    for f in $(ls); do
        sed -e "s|\\\${datadir}|${2}|g" \
            -e "s|\\\${logdir}|${3}|g" \
            -e "s|\\\${tempdir}|${4}|g" \
            "${f}" > "${1}/${f}"
    done
    popd
}

case "$1" in
    "--git-hash")
        echo "${GIT_HASH}"
    ;;
    
    "--git-count")
        echo "${GIT_COUNT}"
    ;;

    "deb")
        build_sql_layer "${STAGE_DIR}/usr/sbin"  "${STAGE_DIR}/usr/share/foundationdb/sql"
        build_client_tools "${STAGE_DIR}/usr/bin" "${STAGE_DIR}/usr/share/foundationdb/sql"
        filter_config_files "${STAGE_DIR}/etc/foundationdb/sql" "/var/lib/foundationdb/sql" "/var/log/foundationdb/sql" "/tmp"

        cp -r "${PACKAGING_DIR}/deb" "${STAGE_DIR}/debian"
        mkdir -p "${STAGE_DIR}/usr/share/doc/fdb-sql-layer/"
        cp "${PACKAGING_DIR}/deb/copyright" "${STAGE_DIR}/usr/share/doc/fdb-sql-layer/"

        cd "${STAGE_DIR}"
        # No sign source, no sign changes, binary only
        debuild -us -uc -b
    ;;

    "rpm")
        if [ -z "$2" ] ; then
            epoch=$(date +%s)
        else
            epoch=$2
        fi

        STAGE_ROOT="${STAGE_DIR}/rpmbuild"
        BUILD_DIR="${STAGE_ROOT}/BUILD"
        build_sql_layer "${BUILD_DIR}/usr/sbin" "${BUILD_DIR}/usr/share/foundationdb/sql"
        build_client_tools "${BUILD_DIR}/usr/bin" "${BUILD_DIR}/usr/share/foundationdb/sql"
        filter_config_files "${BUILD_DIR}/etc/foundationdb/sql" "/var/lib/foundationdb/sql" "/var/log/foundationdb/sql" "/tmp"

        mkdir -p "${BUILD_DIR}/etc/rc.d/init.d/"
        cp "${PACKAGING_DIR}/rpm/fdb-sql-layer.init" "${BUILD_DIR}/etc/rc.d/init.d/fdb-sql-layer"
        
        mkdir -p "${BUILD_DIR}/usr/share/doc/fdb-sql-layer/"
        cp "${TOP_DIR}/LICENSE.txt" ${BUILD_DIR}/usr/share/doc/fdb-sql-layer/LICENSE

        cd "${STAGE_DIR}"
        SPEC_FILE="fdb-sql-layer.spec"
        sed -e "s/_EPOCH/${epoch}/g" \
            -e "s/_GIT_COUNT/${GIT_COUNT}/g" \
            -e "s/_GIT_HASH/${GIT_HASH}/g" \
            "${PACKAGING_DIR}/rpm/${SPEC_FILE}.in" > "${SPEC_FILE}"

        mkdir -p "${STAGE_ROOT}"/{SOURCES,SRPMS,RPMS/noarch}
        rpmbuild --target=noarch --define "_topdir ${STAGE_ROOT}" -bb "${SPEC_FILE}"

        mv "${STAGE_ROOT}"/RPMS/noarch/* "${TOP_DIR}/target/"
    ;;

    "targz")
        STAGE_ROOT="${STAGE_DIR}/targz"

        cd "${TOP_DIR}"
        build_sql_layer "${STAGE_ROOT}/bin" "${STAGE_ROOT}/lib"
        build_client_tools "${STAGE_ROOT}/bin" "${STAGE_ROOT}/lib"
        filter_config_files "${STAGE_ROOT}/conf" "./data" "./logs" "./tmp"

        cp bin/* "${STAGE_ROOT}/bin/"
        cp target/client-tools/bin/* "${STAGE_ROOT}/bin/"
        cp LICENSE.txt "${STAGE_ROOT}/"
        cp README.md "${STAGE_ROOT}/"

        BINARY_NAME="fdb-sql-layer-${LAYER_VERSION}"
        cd "${STAGE_DIR}"
        mv targz "${BINARY_NAME}"
        tar czf "${TOP_DIR}/target/${BINARY_NAME}.tar.gz" "${BINARY_NAME}"
    ;;

    "pkg")
        STAGE_ROOT="${STAGE_DIR}/root"
        STAGE_LOCAL="${STAGE_ROOT}/usr/local"

        build_sql_layer "${STAGE_LOCAL}/libexec" "${STAGE_LOCAL}/foundationdb/sql"
        build_client_tools "${STAGE_LOCAL}/bin" "${STAGE_LOCAL}/foundationdb/sql"
        filter_config_files "${STAGE_LOCAL}/etc/foundationdb/sql" "/usr/local/foundationdb/data/sql" "/usr/local/foundationdb/logs/sql" "/tmp"

        mkdir -p "${STAGE_ROOT}/Library/LaunchDaemons"
        mkdir -p "${STAGE_LOCAL}/foundationdb/logs/sql"

        sed -i "" -e 's/usr\/share/usr\/local/g' "${STAGE_LOCAL}/libexec/fdbsqllayer"
        cp "${TOP_DIR}/LICENSE.txt" "${STAGE_LOCAL}/foundationdb/LICENSE-SQL_LAYER"
        
        cd "${PACKAGING_DIR}/pkg/"
        cp -r resources/ "${STAGE_DIR}/"
        cp "${TOP_DIR}/LICENSE.txt" "${STAGE_DIR}/resources/"
        cp com.foundationdb.layer.sql.plist "${STAGE_ROOT}/Library/LaunchDaemons/"
        cp uninstall-FoundationDB-SQL_Layer.sh "${STAGE_LOCAL}/foundationdb"

        cd "${STAGE_LOCAL}/foundationdb/sql/"
        ln -s /usr/local/foundationdb/sql/fdb-sql-layer-2.0.0-SNAPSHOT.jar fdb-sql-layer.jar
        ln -s /usr/local/foundationdb/sql/fdb-sql-layer-client-tools-1.3.7-SNAPSHOT.jar fdb-sql-layer-client-tools.jar

        cd "${TOP_DIR}/target"
        pkgbuild --root "${STAGE_ROOT}" --identifier com.foundationdb.layer.sql --version ${LAYER_VERSION} --scripts "${PACKAGING_DIR}/pkg/scripts" "${STAGE_DIR}/SQL_Layer.pkg"
        productbuild --distribution "${PACKAGING_DIR}/pkg/Distribution.xml" --resources "${STAGE_DIR}/resources" --package-path "${STAGE_DIR}" FoundationDB-SQL_Layer-${LAYER_VERSION}.pkg
    ;;

    *)
        echo "Usage: $0 [--git-hash | --git-count | deb | rpm [epoc] | pkg | targz]" >&2
        if [ "$1" != "" ]; then
            echo "Invalid Argument: $1" >&2
        fi
        exit 1
    ;;
esac

exit 0
