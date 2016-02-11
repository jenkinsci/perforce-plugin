#/bin/bash

if [ $# -eq 0 ]
	then
		echo "usage: ./build.sh mvn clean package"
		exit 1
fi

docker build -t perforce-plugin-build buildenv && \
docker run -it --rm -v `pwd`:/src -v $HOME/.m2/settings.xml:/root/.m2/settings.xml -v $HOME/.m2/repository:/root/.m2/repository perforce-plugin-build $@
