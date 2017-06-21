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

import net.myzimbra.cloud.methods.Delete;
import net.myzimbra.cloud.methods.Get;
import net.myzimbra.cloud.methods.Head;
import net.myzimbra.cloud.methods.MkCol;
import net.myzimbra.cloud.methods.Move;
import net.myzimbra.cloud.methods.Propfind;
import net.myzimbra.cloud.methods.Put;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.Folder;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;

/**
 * Implements the part of the WebDAV Protocol needed by Owncloud clients
 *
 * - /remote.php/webdav
 *
 * @author Laurent FRANCOISE
 */
public class Remote extends HttpHandler {

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Precondition : The document specified in the URI must exists
     *
     * Status : - Success = 200 - Not Found = 404
     *
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        try {
            Get get = new Get(getAuthUser(), getPath());

            if (!get.exists()) {
                resp.setStatus(404);
            } else {
                resp.setStatus(200);

                if (get.isDocument()) {
                    resp.setHeader("Content-Disposition", "attachment");
                    resp.setHeader("X-OC-MTime", "accepted");
                    resp.setHeader("Etag", get.getEtag());
                    resp.setHeader("OC-Etag", get.getEtag());
                    resp.setHeader("OC-FileId", get.getId());

                    String contentType = get.getContentType();
                    if (contentType != null) {
                        resp.setContentType(contentType);
                    }

                    resp.setContentLength((int) get.getSize());

                    ByteUtil.copy(get.getInputStream(), true, resp.getOutputStream(), true);

                }

            }

        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }

    }

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Preconditions : - The element specified in the URI must not exists - The
     * parent folder must exist
     *
     * Status : - Success = 201 - Folder already exists = 405 - Parent folder
     * missing = 409
     *
     */
    @Override
    public void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MkCol mkCol = new MkCol(getAuthUser(), getPath());

            if (mkCol.isFolder()) {
                resp.setStatus(405);
            } else {
                mkCol.mkcol();
                mkCol.notifyFolders();
                resp.setStatus(201);
            }
        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }
    }

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Precondition : - The folder or document specified in the URI must exists
     *
     */
    @Override
    public void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Delete delete = new Delete(getAuthUser(), getPath());
            if (delete.exists()) {
                Folder parent = delete.getFolder();
                delete.delete();
                delete.notifyFolders(parent);
                resp.setStatus(204);
            } else {
                resp.setStatus(404);
            }
        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }
    }

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Status : - Moving success + resource created = 201 - Moving sucess +
     * target already exists = 204 - Source == target = 403 - Missing parent(s)
     * folder(s) on the target = 409
     */
    @Override
    public void doMove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String target = req.getHeader("Destination");
        try {
            Move move = new Move(getAuthUser(), getPath(), target);
            move.findTargetItem();

            // if the target is a folder, chex that it exists
            /*  if ( move.targetPathIsFolder() && (!move.targetIsFolder()) ) {
                resp.setStatus(409);
            } else {*/
            Folder parent = move.getFolder();
            Folder targetParent = move.getTargetFolder();
            move.move();
            move.notifyFolders(parent);
            if (parent.equals(targetParent)) {
                resp.setStatus(204);
            } else {
                move.notifyFolders(targetParent);
                resp.setStatus(201); // TODO : or 204
            }

            /* }*/
        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }
    }

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Precondition : - The element specified in the URI is not a folder ( The
     * element can be an existing document)
     *
     */
    @Override
    public void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Put put = new Put(getAuthUser(), getPath());

            if (req.getHeader("OC-Chunked") == null) {
                ZimbraLog.extensions.info("NORMAL PUT");
                put.upload(req.getInputStream(), req.getContentType());
                put.notifyFolders();
                resp.setHeader("Etag", put.getEtag());
                resp.setHeader("OC-Etag", put.getEtag());
                resp.setHeader("OC-FileId", put.getId());
                resp.setHeader("X-OC-MTime", "accepted");

            } else {
                ZimbraLog.extensions.info("CHUNKED PUT");
                put.uploadChunk(req.getInputStream());
                if (put.isLastChunk()) {
                    ZimbraLog.extensions.info("LAST CHUNK");
                    put.finishUploadChunk(req.getContentType());
                    put.notifyFolders();
                    resp.setHeader("Etag", put.getEtag());
                    resp.setHeader("OC-Etag", put.getEtag());
                    resp.setHeader("OC-FileId", put.getId());
                    resp.setHeader("X-OC-MTime", "accepted");

                }

            }

            resp.setStatus(201);

        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }
    }

    @Override
    public void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Head head = new Head(getAuthUser(), getPath());

            if (!head.exists()) {
                resp.setStatus(404);
            } else {
                resp.setStatus(200);
            }

        } catch (ServiceException ex) {
            ZimbraLog.extensions.error(ex);
            resp.setStatus(500);
        }
    }

    /**
     *
     * @param req
     * @param resp
     * @throws IOException
     *
     * Precondition : - There's an header 'Depth' with a value 0 or 1 - The URI
     * is an existing docuement or folder
     *
     */
    @Override
    public void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String input = IOUtils.toString(req.getInputStream());

        ZimbraLog.extensions.debug(input);

        String depth = req.getHeader("Depth");

        Propfind propfind;
        try {
            propfind = new Propfind(getAuthUser(), getPath(), depth);
            propfind.parse(input);

            //response
            resp.setContentType("application/xml; charset=utf-8");
            resp.setStatus(207);

            String response = propfind.response();

            ZimbraLog.extensions.debug(response);

            PrintWriter out = resp.getWriter();
            out.print(response);

        } catch (ServiceException ex) {
            ZimbraLog.extensions.debug(ex);
            resp.setStatus(500);
        }

    }

}
