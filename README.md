# MyZimbra Cloud

## What is it ?

MyZimbra Cloud is a (partial) JAVA implementation of an Owncloud server, directly embedded in the Zimbra Server.

You can use all the Owncloud clients (Desktop and Mobile), the files will be synchronised with the Briefcase folder.

** To be clear : no need for a separate Owncloud server here, Zimbra IS the Owncloud server !**

Your credentials will be your email adress/password, and the server will be something like
https://"webmail"/service/cloud

The license of the project is AGPLv3.

## Prerequisites

A Zimbra Server v8.6.x , either NE or OSS (Only one mailstore for now).

Zimbra 8.7.x should be OK for the most part (needs testing !)

## Build

You will need to build the .jar extension, which can be done either in CLI or within an IDE

### Building the .jar in CLI

The build is done on a Zimbra server, preferably the same version you will use in production.

You will need ANT to compile the project, the other components are already provided by Zimbra :

as root :

```
cd /tmp
wget http://apache.mediamirrors.org//ant/binaries/apache-ant-1.10.1-bin.zip
cd /usr/local/
unzip /tmp/apache-ant-1.10.1-bin.zip
ln -s apache-ant-1.10.1/ ant
```

Then grab the source code :

```
cd /usr/src
git clone "git repo"
chown -R zimbra.zimbra myzimbracloud
```

As the zimbra user :

```
su - zimbra
cd /usr/src/myzimbracloud
export PATH=$PATH:/usr/local/ant/bin
export LANG=fr_FR.UTF-8 # or "your language".UTF-8
export LC_ALL=fr_FR.UTF-8 # ditto
```

The build should look like this :

```
$ ant
Buildfile: /usr/src/myzimbracloud/build.xml
Trying to override old definition of task javac

init:
    [mkdir] Created dir: /usr/src/myzimbracloud/build

compile:
    [javac] Compiling 16 source files to /usr/src/myzimbracloud/build
    [javac] Note: Some input files use or override a deprecated API.
    [javac] Note: Recompile with -Xlint:deprecation for details.

dist:
      [jar] Building jar: /usr/src/myzimbracloud/dist/CloudServlet.jar

BUILD SUCCESSFUL
Total time: 1 second
```

### Building the .jar with an IDE

Only the general directions here :

  * Set up a new Java project type 'Java CLI'
  * Get all the .jar from a Zimbra server (/opt/zimbra/lib/jars/*.jar) and add them to your project
  * exclude theses libs from the .jar you are building

## Install



Edit '/opt/zimbra/jetty/etc/service.web.xml.in and add the following lines (just below the servlet-mapping DavServlet section) :

```
 <servlet-mapping>
    <servlet-name>CloudServlet</servlet-name>
    <url-pattern>/cloud/*</url-pattern>
  </servlet-mapping>
```

and add the following lines (below the servlet DavServler section)

```
 <servlet>
    <servlet-name>CloudServlet</servlet-name>
    <servlet-class>net.myzimbra.cloud.CloudServlet</servlet-class>
    <async-supported>true</async-supported>
    <init-param>
      <param-name>allowed.ports</param-name>
      <param-value>%%zimbraMailPort%%, %%zimbraMailSSLPort%%</param-value>
    </init-param>
    <load-on-startup>10</load-on-startup>
  </servlet>
```


Copy CloudServlet.jar to the lib folder of the 'service' webapp , then restart zmmailboxd

as zimbra:
```
cp /usr/src/myzimbracloud/dist/CloudServlet.jar /opt/zimbra/jetty/webapps/service/WEB-INF/lib
zmmailboxdctl restart
```

You will also need to install the zimlet net_myzimbra_cloud on the zimbra server :

as zimbra (you will need the 'zip' command) :
```
cd /usr/src/myzimbracloud/net_myzimbra_cloud
bash make_zimlet.sh
cp net_myzimbra_cloud.zip /opt/zimbra/zimlets
cd /opt/zimbra/zimlets
zmzimletctl deploy net_myzimbra_cloud.zip
```

Then enable net_myzimbra_cloud for the users that will use the extension.

####A word about the Zimlet :

Why do we need a Zimlet, as all the magic happens server-side ?

The reason is to be looked at the way Owncloud works : each time you update a file, all
the parents folders will be recursively notified. That's how the Owncloud clients monitor a file change
somewhere, by looking at an Etag change at the root folder level.

The zimlet does just that in the webmail, each time you update something in the Briefcase.

Without this the Zimlet, if you modify something in the webmail, all clients will be out of sync.

## Configure

Create the file /opt/zimbra/conf/cloud.conf with the following content

```
{
 "url_webmail":"https://<the public url of my Zimbra server>",
 "check_zimlet":"net_myzimbra_cloud"
}
```

Then restart zmmailboxd.

You can check that all is OK by looking at mailbox.log :

```
$ grep -i cloud ~/log/mailbox.log
2017-06-06 11:36:31,839 INFO  [main] [] extensions - Servlet CloudServlet starting up
2017-06-06 11:36:31,846 INFO  [main] [] extensions - Cloud Servlet : Config file OK.
2017-06-06 11:36:31,846 INFO  [main] [] soap - Adding service CloudExtnService to SoapServlet
```

## Run

Download an Owncloud Desktop Client here https://owncloud.org/install/ , then use the folowing parameters :

  * server address : https://"your Zimbra server"/service/cloud
  * username : your email address
  * password : your email password
   
Then follow the assistant as you would with an Owncloud server.