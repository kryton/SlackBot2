SlackBot2
=======

An POC app which connects to various social chat/messenger networks and internal DR systems.


Some functional features:
-------------------------

Some non-functional features:
-----------------------------

- uses Docker to package this up in a container, to make it easier to run/install

- Uses Scala & Play


Configuration:
--------------

1. copy the 'xxxx.sh.dist' files in the home directory to their xxx.sh counterparts. the main thing to configure here is the location of your private repository that you will be pushing/pulling from.

2. locate your photos, and put them in either 'source' or 'cache' directory, and edit run.sh to point to them. 'source' files are cropped/adjusted, and the cached version is placed in 'cache'.
There is a java file called util.BatchImageProcess that will do a bulk conversion for you.

3. copy the conf/secret.conf.template to conf/secret.conf and replace the 'example.com' machines with your own.


4. run './build.sh' and it should build your docker container than you can 'run.sh' ..

> $ ./build.sh <-- builds the container

> $ ./nuke.sh  <-- removes old versions

> $ ./run.sh   <-- runs the new one

5. alternatively you can just run it without docker via

> $ sbt run

or

> $ activator run
