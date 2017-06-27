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

import net.myzimbra.cloud.soap.CloudExtnService;
import com.zimbra.common.account.Key;
import com.zimbra.common.account.Key.AccountBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.HttpUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.GuestAccount;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.auth.AuthContext;
import com.zimbra.cs.service.AuthProvider;
import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.soap.SoapServlet;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;

/**
 * CloudServlet Zimbra extension - Main Class
 *
 * Tries to emulate a real Owncloud Server by implementing the following routes :
 *
 * - /service/cloud/status.php 
 * - /service/cloud/remote.php/webdav 
 * - /service/cloud/ocs/v1.php 
 * - /service/cloud/ocs/v2.php 
 * - /index.php/ocs/cloud
 *
 * @author Laurent FRANCOISE
 */
public class CloudServlet extends ZimbraServlet {

    private Account mAuthUser = null;
    
    protected static final String AUTHENTICATE_HEADER = "WWW-Authenticate";

    @Override
    public void init() throws ServletException {
        String name = getServletName();
        ZimbraLog.extensions.info("Servlet " + name + " starting up");

        try {
            Config.getInstance().read();
            ZimbraLog.extensions.info("Cloud Servlet : Config file OK.");
        } catch (IOException | JSONException ex) {
            ZimbraLog.extensions.warn("Cloud Servlet : Error reading config file. Using default values.");
        }

        super.init();

        // SOAP Call "NotifyFolders"
        SoapServlet.addService("SoapServlet", new CloudExtnService());

    }

    @Override
    public void destroy() {
        String name = getServletName();
        ZimbraLog.extensions.info("Servlet " + name + " shutting down");
        super.destroy();

    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        ZimbraLog.clearContext();
        addRemoteIpToLoggingContext(req);

        String uri = req.getRequestURI();
        int port = req.getLocalPort();
        String method = req.getMethod();

        HttpHandler handler = null;

        if (uri.equals(Config.URL_CLOUD_PREFIX + "/status.php")) {
            handler = new Status();
        } else if (uri.equals(Config.URL_CLOUD_PREFIX)) {
            String url = Config.getInstance().webmailURL();
            if (url != null) {
                resp.sendRedirect(url);
            }
        } else if (uri.startsWith(Config.URL_CLOUD_PREFIX + "/remote.php/webdav")) {
            if (authUser(req, resp) == true) {
                handler = new Remote();
                handler.setAuthUser(mAuthUser);
                handler.setPath(uri.replace(Config.URL_CLOUD_PREFIX + "/remote.php/webdav", ""));
            } else {
                resp.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } else if (uri.startsWith(Config.URL_CLOUD_PREFIX + "/ocs/v1.php")
                || uri.startsWith(Config.URL_CLOUD_PREFIX + "/ocs/v2.php")) {
            handler = new Ocs();
        } else if (uri.startsWith(Config.URL_CLOUD_PREFIX + "/index.php/ocs/cloud")) {
            if (authUser(req, resp) == true) {
                handler = new Ocs();
                handler.setAuthUser(mAuthUser);
            }
        }

        ZimbraLog.extensions.info("cloudServlet - uri=" + uri + ", port=" + port + ", method=" + method);

        if (handler == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            switch (method) {
                case "GET":
                    handler.doGet(req, resp);
                    break;
                case "PUT":
                    handler.doPut(req, resp);
                    break;
                case "HEAD":
                    handler.doHead(req, resp);
                    break;
                case "DELETE":
                    handler.doDelete(req, resp);
                    break;
                case "MOVE":
                    handler.doMove(req, resp);
                    break;
                case "PROPFIND":
                    handler.doPropfind(req, resp);
                    break;
                case "MKCOL":
                    handler.doMkcol(req, resp);
                    break;
                default:
                    resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }

        }

    }

    @Override
    protected String getRealmHeader(HttpServletRequest req, Domain domain) {
        return "Basic realm=\"ownCloud\"";
    }

    @Override
    public Account basicAuthRequest(HttpServletRequest req, HttpServletResponse resp, boolean sendChallenge)
            throws IOException, ServiceException {

        if (!AuthProvider.allowBasicAuth(req, this)) {
            return null;
        }

        String auth = req.getHeader("Authorization");

        // TODO: more liberal parsing of Authorization value...
        if (auth == null || !auth.startsWith("Basic ")) {
            if (sendChallenge) {
                resp.addHeader(AUTHENTICATE_HEADER, getRealmHeader(req, null));
                ZimbraLog.dav.debug("calling sendError [%s] 'must authenticate'", HttpServletResponse.SC_UNAUTHORIZED);
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "must authenticate");
            }
            return null;
        }

        // 6 comes from "Basic ".length();
        String userPass = new String(Base64.decodeBase64(auth.substring(6).getBytes()), "UTF-8");

        int loc = userPass.indexOf(":");
        if (loc == -1) {
            ZimbraLog.dav.debug("calling sendError [%s] 'invalid basic auth credentials'",
                    HttpServletResponse.SC_BAD_REQUEST);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid basic auth credentials");
            return null;
        }

        String userPassedIn = userPass.substring(0, loc);
        String user = userPassedIn;
        String pass = userPass.substring(loc + 1);

        Provisioning prov = Provisioning.getInstance();

        if (user.indexOf('@') == -1) {
            String host = HttpUtil.getVirtualHost(req);
            if (host != null) {
                Domain d = prov.get(Key.DomainBy.virtualHostname, host.toLowerCase());
                if (d != null) {
                    user += "@" + d.getName();
                }
            }
        }
        ZimbraLog.dav.debug("Auth user passed in '%s'.  User being used '%s'", userPassedIn, user);

        Account acct = prov.get(AccountBy.name, user);
        if (acct == null) {
            if (sendChallenge) {
                resp.addHeader(AUTHENTICATE_HEADER, getRealmHeader(req, null));
                ZimbraLog.dav.debug("calling sendError [%s] 'invalid username/password'",
                        HttpServletResponse.SC_UNAUTHORIZED);
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid username/password");
                return null;  // Makes no sense to return a guest account if we've already done sendError
            }
            return new GuestAccount(user, pass);
        }
        try {
            Map<String, Object> authCtxt = new HashMap<>();
            authCtxt.put(AuthContext.AC_ORIGINATING_CLIENT_IP, ZimbraServlet.getOrigIp(req));
            authCtxt.put(AuthContext.AC_REMOTE_IP, ZimbraServlet.getClientIp(req));
            authCtxt.put(AuthContext.AC_ACCOUNT_NAME_PASSEDIN, userPassedIn);
            authCtxt.put(AuthContext.AC_USER_AGENT, req.getHeader("User-Agent"));
            prov.authAccount(acct, pass, AuthContext.Protocol.http_basic, authCtxt);
        } catch (ServiceException se) {
            if (sendChallenge) {
                resp.addHeader(AUTHENTICATE_HEADER, getRealmHeader(req, prov.getDomain(acct)));
                ZimbraLog.dav.debug("calling sendError [%s] 'invalid username/password'",
                        HttpServletResponse.SC_UNAUTHORIZED);
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid username/password");
            }
            return null;
        }
        return acct;
    }

    private boolean authUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            mAuthUser = basicAuthRequest(req, resp, true);
        } catch (ServiceException e) {
            ZimbraLog.extensions.error("error getting authenticated user", e);
        }
        
        if (mAuthUser == null) {
            return false;
        }

        // NO authentication if the zimlet Config.getInstance().checkZimlet() is defined but not present
        String check_zimlet = Config.getInstance().checkZimlet();
        if (check_zimlet != null) {
            String[] zimlets = mAuthUser.getZimletAvailableZimlets();
            for (String zimlet : zimlets) {
                if (zimlet.contains(check_zimlet)) {
                    ZimbraLog.addToContext(ZimbraLog.C_ANAME, mAuthUser.getName());
                    return true;
                }
            }
            ZimbraLog.extensions.error("Login failed for user "+mAuthUser.getName()+" : zimlet "+check_zimlet+" not found.");
        } else {
            return true;
        }

        return false;

    }

}
