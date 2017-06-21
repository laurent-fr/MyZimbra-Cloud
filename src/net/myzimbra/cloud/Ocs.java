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

import com.zimbra.common.util.ZimbraLog;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Answers HTTP requests with 'ocs' in the URL
 *
 * - /ocs/v1.php 
 * - /ocs/v2.php 
 * - /index.php/ocs/cloud
 *
 * @author Laurent FRANCOISE
 */
class Ocs extends HttpHandler {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        ZimbraLog.extensions.info("OCS :" + req.getRequestURI());

        PrintWriter out = resp.getWriter();

        if (req.getRequestURI().contains("cloud/user")) {
            resp.setContentType("application/json; charset=utf-8");
            out.print("{\"ocs\":{\"meta\":{\"status\":\"ok\",\"statuscode\":100,\"message\":null},\"data\":{\"id\":\"" + getAuthUser() + "\",\"display-name\":\"" + getAuthUser() + "\",\"email\":\"\"}}}");

        } else if (req.getRequestURI().contains("shares")) {
            resp.setContentType("text/xml; charset=UTF-8");
            out.print("<?xml version=\"1.0\"?>\n"
                    + "<ocs>\n"
                    + " <meta>\n"
                    + "  <status>ok</status>\n"
                    + "  <statuscode>100</statuscode>\n"
                    + "  <message/>\n"
                    + " </meta>\n"
                    + " <data/>\n"
                    + "</ocs>");

        } else {
            resp.setContentType("application/json; charset=utf-8");
            out.print("{\"ocs\":{\"meta\":{\"status\":\"ok\",\"statuscode\":100,\"message\":null},\"data\":{\"version\":{\"major\":8,\"minor\":2,\"micro\":2,\"string\":\"8.2.2\",\"edition\":\"\"},\"capabilities\":{\"core\":{\"pollinterval\":60},\"files_sharing\":{\"api_enabled\":false,\"public\":{\"enabled\":false,\"password\":{\"enforced\":false},\"expire_date\":{\"enabled\":false},\"send_mail\":false,\"upload\":true},\"user\":{\"send_mail\":false},\"resharing\":false,\"federation\":{\"outgoing\":true,\"incoming\":true}},\"files\":{\"bigfilechunking\":false,\"undelete\":true,\"versioning\":true},\"notifications\":{\"endpoints\":[\"get\",\"delete\"]}}}}}");
        }

    }

}
