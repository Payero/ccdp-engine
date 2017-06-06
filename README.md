# Cloud Computing Data Processing (CCDP)

## Processing Engine

### Overview
Cloud Computing Data Processing (CCDP) is an IRAD whose intention is to create a 
framework to easily interact with the most common services offered by the cloud 
provider for data processing.  The framework takes the burden away from 
knowledge about the cloud provider from the processing modules.  It aims to 
facilitate an environment that is dynamically modified be either allocating or 
deallocating resources as needed.  This dynamic environment will allow 
maximizing the resources usage without compromising processing threads.  A 
processing thread is a sequence of processing modules used to generate some 
results.  The framework provides a way to allow communications between the 
modules

  
### Minimum Requirements:

- Java SDK 8
- Ant 1.7

### Installing

- Decompress the file containing all the source code
- cd to the root directory
- Type ant and hit enter


### Running the Main Application




- First, build the docker images. Thankfully, `docker-compose` provides a way to orchestrate multiple Docker containers.

    ```
    docker-compose build
    ```

- Then, run the docker images.

    ```
    docker-compose up
    ```

- At this point, mongo may be empty, so we provide `modules-mongo.json` for seeding. To seed the database, copy `modules-mongo.json` to `ccdp/webapp/data/`. Then, enter the docker container running mongo and import the data. Below illustrates each step.

    ```
    ~/ccdp $ mkdir -p webapp/data # you may need to make a data/ subdir under webapp/
    ~/ccdp $ cp modules-mongo.json webapp/data/. # copy json seed file to data dir (data dir is mounted as volume to mongo docker container)
    ~/ccdp $ docker ps # view the currently running docker containers
    CONTAINER ID        IMAGE               COMMAND                  CREATED             STATUS              PORTS                     NAMES
    9d80e64e2621        webapp_app          "python server.py --i"   11 minutes ago      Up 10 minutes       0.0.0.0:20223->5000/tcp   webapp_app_1
    8ca20944891b        mongo:3.2           "/entrypoint.sh mongo"   28 minutes ago      Up 10 minutes       27017/tcp                 webapp_db_1
    ~/ccdp $ docker exec -it 8ca20944891b bash -l # enter the container with ID=8ca20944891b and execute `bash -l` (giving you an interactive shell)
    root@mongo:/# mongoimport --db ccdp --collection modules --jsonArray /data/db/modules-mongo.json
    2016-07-12T18:28:41.963+0000	connected to: localhost
    2016-07-12T18:28:41.969+0000	imported 3 documents
    ```

- Now, by navigating to localhost:20223 (if you are running this on another server, try running <IP_OF_SERVER>:20223), you should be able to see the webapp with several modules listed.

### To Do:
- Add running on a single VM to the Thread Request
- Need to add different images based on node types!!
- How to handle lambda tasks


