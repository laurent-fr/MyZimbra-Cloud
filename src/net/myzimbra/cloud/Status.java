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

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Answers HTTP requests with 'status' in the URL
 *
 * - /status.php
 *
 * @author Laurent FRANCOISE
 */
public class Status extends HttpHandler {

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("application/json");

        PrintWriter out = resp.getWriter();
        out.print("{\"installed\":true,\"maintenance\":false,\"version\":\"8.2.2.2\",\"versionstring\":\"8.2.2\",\"edition\":\"\"}");

    }

}
