
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



The command line to get stats from docker stats
docker stats --no-stream --format '{"container": "{{.Container}}","container-name": "{{.Name}}","container-id": "{{.ID}}","container-cpu": "{{.CPUPerc}}","container-mem": "{{.MemUsage}}","container-net": "{{.NetIO}}","container-block": "{{.BlockIO}}","container-mem-perc": "{{.MemPerc}}","container-pids": "{{.PIDs}}",}'


# You can modify dockerd behavior by adding the options to the 
# /usr/lib/systemd/system/docker.service file.  
# ExecStart=/usr/bin/docker -d -H tcp://127.0.0.1:4243 -H fd:// $OPTIONS
#

# To get IP Address
docker inspect <container id> | grep IPAddress


# To post a file (http1.json) using curl: 
curl -X PUT --data-binary @http1.json http://localhost:8500/v1/agent/service/register
