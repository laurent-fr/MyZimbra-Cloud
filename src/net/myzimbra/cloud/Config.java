/*
* MyZimbra Cloud - Owncloud clients synchronized directly with Zimbra briefcase
* Copyright (C) 2016-2017  Laurent FRANCOISE
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
 */
package net.myzimbra.cloud;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Config constants
 *
 * @author Laurent FRANCOISE
 */
public class Config {

    // the config file ...
    public final static String CONFIG_FILE = "/opt/zimbra/conf/cloud.conf";

    // prefix of the URL 
    public final static String URL_CLOUD_PREFIX = "/service/cloud";

    // public url of the webmail ( "https://zimbra.mycompany.com" )
    private static String mURLWebmail = null;

    // auth will fail if this zimlet is not present (unless undefined)
    // default : null (undefined)
    private static String mCheckZimlet = null;

    // Singleton
    private static Config INSTANCE = new Config();

    private Config() {

    }

    public static Config getInstance() {
        return INSTANCE;
    }

    public String webmailURL() {
        return mURLWebmail;
    }

    public String checkZimlet() {
        return mCheckZimlet;
    }

    public void read() throws IOException, JSONException {

        File file = new File(CONFIG_FILE);
        String content = FileUtils.readFileToString(file, "utf-8");
        JSONObject json = new JSONObject(content);

        mURLWebmail = json.getString("url_webmail");
        mCheckZimlet = json.getString("check_zimlet");

    }

}
