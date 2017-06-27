#!/bin/bash
# MyZimbra Cloud - Owncloud clients synchronized directly with Zimbra briefcase
# Copyright (C) 2016-2017  Laurent FRANCOISE
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

echo -e "\n\e[33mMyZimbra Cloud installer\n------------------------\e[39m\n"

INSTALL_SCRIPT=$(readlink -f $0)
INSTALL_PATH=$(dirname $INSTALL_SCRIPT)
INSTALL_LANG=$LANG
INSTALL_LC_ALL=$LC_ALL

MSG_DONE=" \e[32mdone.\e[39m"

# script must be run as root
if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root !"
   exit 1
fi

# check if some commands are installed
if [ ! -x $(which wget) ]
then  
    echo "Please install the 'wget' command !" 
    exit -1
fi

if [ ! -x $(which unzip) ]
then  
    echo "Please install the 'unzip' command !" 
    exit -1  
fi

if [ ! -x $(which zip) ]
then  
    echo "Please install the 'zip' command !" 
    exit -1  
fi


echo -e "\e[31mBeware ! \e[39m\n"
cat <<EOF
This script will install all the files needed to run MyZimbra Cloud, but as
always with things found on the Internet you HAVE to review the content and
perform a test on a non-production server before doing the real thing.

Proceed at your own risk !

EOF

read -p "Are you sure (y/n) ? " -n 1 -r
echo   
if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo -e "\n\e[32mOK, let's go !\e[39m\n"
else
    exit -1
fi

echo "Please enter the public URL of your Zimbra server , if you press ENTER"
echo -e "the default value will be \e[33mhttps://$(hostname -f)\e[39m :"
read -e -p "? " -i "https://$(hostname -f)" zimbra_public_url

# install

echo "Downloading and installing ant ..."

cd /tmp && wget -N http://apache.mediamirrors.org//ant/binaries/apache-ant-1.10.1-bin.zip
cd /usr/local/ && unzip -q -o /tmp/apache-ant-1.10.1-bin.zip && ln -sf apache-ant-1.10.1/ ant
echo -e $MSG_DONE

echo "Copying sources ..."
if [ -f ${INSTALL_PATH}/../README.md ] 
then
    mkdir -p /usr/src/myzimbracloud && cp -a ${INSTALL_PATH}/../* /usr/src/myzimbracloud/ && chown -R zimbra.zimbra /usr/src/myzimbracloud
else
    echo "Error !"
    exit -1
fi
echo -e $MSG_DONE

echo "Compiling the servlet ..."
sudo INSTALL_LANG=${INSTALL_LANG} INSTALL_LC_ALL=${INSTALL_LC_ALL} -u zimbra -i bash -c  'cd /usr/src/myzimbracloud && export PATH=$PATH:/usr/local/ant/bin && export LANG=$INSTALL_LANG && export LC_ALL=$INSTALL_LC_ALL && ant'
echo -e $MSG_DONE

echo "Installing Cloud Servlet ..."
cp /usr/src/myzimbracloud/dist/CloudServlet.jar /opt/zimbra/jetty/webapps/service/WEB-INF/lib
chown zimbra.zimbra /opt/zimbra/jetty/webapps/service/WEB-INF/lib/CloudServlet.jar
echo -e $MSG_DONE

echo "Patching service.web.xml.in (in cloud-service.web.xml.in) ..."
if grep -q CloudServlet /opt/zimbra/jetty/etc/service.web.xml.in
then
   echo "Already patched."
else
    backup_service_web=/opt/zimbra/jetty/etc/$(date +%Y%m%d-%H%M%S)-service.web.xml.in
    cp /opt/zimbra/jetty/etc/service.web.xml.in $backup_service_web
    cp /opt/zimbra/jetty/etc/service.web.xml.in  /opt/zimbra/jetty/etc/cloud-service.web.xml.in
    patch -i $INSTALL_PATH/service.web.xml.in.patch /opt/zimbra/jetty/etc/cloud-service.web.xml.in
fi
echo -e $MSG_DONE

echo "Generating config file ..."
cat <<EOF >/opt/zimbra/conf/cloud.conf 
{
 "url_webmail":"${zimbra_public_url}",
 "check_zimlet":"net_myzimbra_cloud"
}
EOF
chown zimbra.zimbra /opt/zimbra/conf/cloud.conf
echo -e $MSG_DONE

echo "Building Zimlet ..."
cd /usr/src/myzimbracloud/net_myzimbra_cloud && bash make_zimlet.sh && chown zimbra.zimbra net_myzimbra_cloud.zip && cp -f net_myzimbra_cloud.zip /opt/zimbra/zimlets
echo -e $MSG_DONE

echo -e "\n\e[33mAlmost there ! \e[39m"

cat <<EOF

Here are the steps to finish the setup :

1) review the content of the file /opt/zimbra/jetty/etc/cloud-service.web.xml.in, and
   if you're OK :

cp /opt/zimbra/jetty/etc/cloud-service.web.xml.in /opt/zimbra/jetty/etc/service.web.xml.in

Note : a backup of the orignal file is available at 
       $backup_service_web 

2) install net_myzimbra_cloud zimlet (zimbra user) :

su - zimbra
cd /opt/zimbra/zimlets
zmzimletctl deploy net_myzimbra_cloud.zip

Don't forget to activate the zimlet for your users, otherwise they won't be able to log
on the Owncloud client !

3) restart the zmmailbox service (zimbra user) :

zmmailboxdctl restart # impact on production !

EOF