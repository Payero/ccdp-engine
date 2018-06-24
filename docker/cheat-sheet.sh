
# creates a new container running as daemon (-d) called test-nginx (--name) and
# maps a port from the container (80) as 5555 in the local host.  It also maps
# a volume (-v) from the localhost as /usr/share/nginx/html and makes it read 
# only (:ro) from the image called nignx:latest
docker run -d --name test-nginx -p 5555:80 -v $(pwd):/usr/share/nginx/html:ro nginx:latest

# Gets information about the container
docker inspect test-nginx

# Using a Dockerfile

# Creates a container from ubuntu:latest and runs apt-get update and 
# apt-get install.  Sets the environment variable so it can be used inside
# the container and uses /data/docker/examples/curl as the working directory
# in the host computer.  It creates a volume by mapping the /data/docker/examples/curl
# from the host computer as /data in the container.  Once all this is done it runs
# the CMD when the container starts
#
FROM ubuntu:latest  
RUN apt-get update  
RUN apt-get install --no-install-recommends --no-install-suggests -y curl  
ENV SITE_URL https://google.com/  
WORKDIR /data/docker/examples/curl  
VOLUME /data/docker/examples/curl:/data
CMD sh -c "curl -L $SITE_URL > /data/results"



# docker run -it --memory=512mb --rm -v /data/ccdp:/data/ccdp <image> bash


# To clean everything in the system that is not being used
docker system prune -a

#
# You can configure dockerd by changing the file /etc/docker/daemon.json 
# followed by sudo service docker restart
#


The command line to get stats from docker stats
docker stats --no-stream --format '{"container": "{{.Container}}","container-name": "{{.Name}}","container-id": "{{.ID}}","container-cpu": "{{.CPUPerc}}","container-mem": "{{.MemUsage}}","container-net": "{{.NetIO}}","container-block": "{{.BlockIO}}","container-mem-perc": "{{.MemPerc}}","container-pids": "{{.PIDs}}"}'

Running the Test:

sudo systemctl start docker
# Where Dockerfile is run
docker build -t payero/ccdp:test .

# then to run the container
docker run -it --memory=512mb --rm -v /data/ccdp:/data/ccdp payero/ccdp:test bash

# to stress the CPU
/data/ccdp/python/ccdp_mod_test.py -a testCpuUsage -p 10


# Resource files can be found on host machine at
/sys/fs/cgroup/memory|cpuacct/docker will contain all the container files



# CONNECTING TO REMOTE DOCKER ENGINE docker -H=172.17.0.1:2375 stats

# --net=host exposes the ports from the host to the conainer
# -e passes the environment variable to the docker container where the docker engine is
# docker run -it --net=host -e DOCKER_HOST=172.17.0.1:2375 --rm -v /data/ccdp:/data/ccdp payero/centos-7:test

# after that I can run docker stats and  should see the docker engine running on my host

# to get the container id from inside the container: (WHOLE THING)
# cat /proc/self/cgroup | grep "docker" | sed s/\\//\\n/g | tail -1 
# 
# Gets the first 12 characters as it is what is shown on cpu stats
# cat /proc/self/cgroup | grep "docker" | sed s/\\//\\n/g | tail -1 | cut -c1-12




Modify an image and commit it/push it
#create a container with an original image and start it
docker run --name nginx-template-base -p 8080:80 -e TERM=xterm -d nginx

# MAKE SURE YOU KNOW THE CONTAINER ID AS YOU WILL NEED IT

# access it and make changes
docker exec -it CONTAINER_ID bash

# exit the container
# commit the changes using the container ID
docker commit CONTAINER_ID payero/<base name>:<tag>

# push the changes to Docker.Hub
docker push payero/<base name>:<tag>


DOCKER HTTP API(https://docs.docker.com/engine/api/v1.21/#21-containers)
# get containers 
curl http://172.17.0.1:2375/containers/json?all=1
