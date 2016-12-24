#!/usr/bin/env bash

if [ $# -ne 1 ]
then
    echo "Usage: $0 FILE" >&2
    exit 1
fi

RUBY_PATH="$(which ruby)"
RAKE_PATH="$(which rake)"
RSPEC_PATH="$(which rspec)"
RUBY_ARGS='$stdout.sync=true;$stderr.sync=true;load($0=ARGV.shift)'
TEST_RUNNER_PATH="$(pwd)/runner/my_runner.rb"
SQLITE3_LIB_PATH="$(gem path sqlite3)/lib"
TYPE_TRACKER_PATH="$(pwd)/type_tracker.rb"
TRACKER="-I${SQLITE3_LIB_PATH} -r${TYPE_TRACKER_PATH}"

if [ "$(uname)" == "Darwin" ]
then
    TIMEOUT="gtimeout -s 9 -k 3m 3m"
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]
then
    TIMEOUT="timeout -s 9 -k 3m 3m"
fi

function run_tests_for_gem_in_path {
    cd "$1" &>/dev/null

    result=$?
    if [ ${result} -ne 0 ]
    then
        return 1
    fi

    bundle install &>/dev/null
    RUBYOPT="${TRACKER}" ${TIMEOUT} ruby -e "${RUBY_ARGS}" "${RAKE_PATH}" &>/dev/null
    result=$?
    if [ ${result} -ne 0 ]
    then
        if [ -d "spec" ]
        then
            RUBYOPT="${TRACKER}" ${TIMEOUT} ruby -e "${RUBY_ARGS}" "${RSPEC_PATH}" . --pattern **/*_spec.rb &>/dev/null
        fi

#        if [ -d "test" ]
#        then
#            RUBYOPT="${TRACKER}" ${TIMEOUT} ruby -e "${RUBY_ARGS}" -Itest "${TEST_RUNNER_PATH}" &>/dev/null
#        fi
    fi

    cd ~-
    return 0
}


while read -r gem version gem_path count
do
    echo "${gem}-${version} starts @ $(date +'%T')"

    result=1
    if [[ "${gem_path}" =~ "github" ]]
    then
        git clone --branch "v${version}" "${gem_path%/}.git" &>/dev/null
        result=$?
        if [ ${result} -ne 0 ]
        then
            git clone --branch "${version}" "${gem_path%/}.git" &>/dev/null
            result=$?
        fi
        echo git
        run_tests_for_gem_in_path "${gem}"
        result=$?
        rm -rf "${gem}" &>/dev/null
    fi

#    if [ ${result} -ne 0 ]
#    then
        gem fetch "${gem}" -v "${version}" # &>/dev/null
        gem unpack "${gem}-${version}.gem" # &>/dev/null
        echo gem
        run_tests_for_gem_in_path "${gem}-${version}"
        rm -rf "${gem}-${version}.gem" # &>/dev/null
        rm -rf "${gem}-${version}" # &>/dev/null
#    fi

done < $1
