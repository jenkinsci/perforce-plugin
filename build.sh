#/bin/bash

if [ $# -eq 0 ]
	then
		echo "usage: ./build.sh clean package"
		exit 1
fi

docker build -t perforce-plugin-build buildenv && \
docker run -it --rm -e USER=$USER -u $UID -v `pwd`:/src -v $HOME/.m2/settings.xml:/home/.m2/settings.xml -v $HOME/.m2/repository:/home/.m2/repository -e HOME=/home perforce-plugin-build mvn -s /home/.m2/settings.xml -Dmaven.repo.local=/home/.m2/repository $@
