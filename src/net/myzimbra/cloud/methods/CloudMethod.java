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
package net.myzimbra.cloud.methods;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.OperationContext;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import org.apache.commons.lang.RandomStringUtils;

/**
 * Zimbra side methods implementation base class
 *
 * @author Laurent FRANCOISE
 */
public class CloudMethod {

    protected String mPath;
    protected Account mAccount;

    protected OperationContext mOpCtxt;
    protected Mailbox mMbox;
    protected MailItem mItem;

    private static final String[] RESERVED_CHARS = {"+"};
    private static final String[] ENCODED_CHARS = {"%2b"};

    public CloudMethod(Account account, String path) throws ServiceException {
        mAccount = account;
        mPath = path;

        if (!mPath.startsWith("/")) {
            mPath = "/" + mPath;
        }

        mPath = "/Briefcase" + mPath;
        ZimbraLog.extensions.debug("PATH BEFORE DECODING:" + mPath);

        try {
            mPath = pathDecode(mPath);
        } catch (UnsupportedEncodingException ex) {
            ZimbraLog.extensions.error("GET unsupported encoding " + mPath);
        }

        ZimbraLog.extensions.debug("PATH :" + mPath);
        mMbox = MailboxManager.getInstance().getMailboxByAccount(mAccount);
        mOpCtxt = new OperationContext(mAccount);

        findItem();

    }

    // http://www.java2s.com/Tutorial/Java/0320__Network/DecodingandencodingURLs.htm
    protected String pathDecode(String value) throws UnsupportedEncodingException {
        // TODO: we actually need to do a proper URI analysis here according to
        // http://tools.ietf.org/html/rfc3986
        for (int i = 0; i < RESERVED_CHARS.length; i++) {
            if (value.indexOf(RESERVED_CHARS[i]) != -1) {
                value = value.replace(RESERVED_CHARS[i], ENCODED_CHARS[i]);
            }
        }

        return URLDecoder.decode(value, "UTF-8");
    }

    public MailItem getItem() {
        return mItem;
    }

    // TODO
    public String getChangeDate() {
        long changeDate = mItem.getChangeDate();
        return ""; // format ?

    }

    public long getSize() {
        return mItem.getSize();
    }

    public Folder getFolder() {

        if (mItem == null) {
            return null;
        }

        Folder folder;
        try {
            folder = mMbox.getFolderById(mOpCtxt, mItem.getFolderId());
        } catch (ServiceException ex) {
            return null;
        }
        return folder;
    }

    public final void findItem() {
        mItem = null;
        try {
            mItem = mMbox.getItemByPath(mOpCtxt, mPath);

        } catch (ServiceException e) {
            if (!(e instanceof MailServiceException.NoSuchItemException)) {
                ZimbraLog.extensions.error(e);
                //throw new IOException();
            }
        }
    }

    public boolean exists() {
        return mItem != null;
    }

    public boolean isDocument() {
        return mItem instanceof com.zimbra.cs.mailbox.Document;
    }

    public boolean isFolder() {
        return mItem instanceof Folder;
    }

    public InputStream getInputStream() throws ServiceException {
        return mItem.getContentStream();
    }

    public String getContentType() {
        if (isDocument()) {
            com.zimbra.cs.mailbox.Document doc = (com.zimbra.cs.mailbox.Document) mItem;
            return doc.getContentType();
        }
        return null;
    }

    public String getEtag() {

        String etag = "";
        if (isFolder()) {
            etag = String.valueOf(mItem.getUnderlyingData().modMetadata); //folder
        }
        if (isDocument()) {
            etag = String.valueOf(mItem.getUnderlyingData().modContent); //document
        }

        return etag;
    }

    public String getId() {
        return mItem.getUuid().replace("-", "");
    }

    public void createFolder() throws ServiceException {
        Folder.FolderOptions fopt = new Folder.FolderOptions().setDefaultView(MailItem.Type.DOCUMENT);
        Folder f = mMbox.createFolder(mOpCtxt, mPath, fopt);
    }

    public void delete() throws ServiceException {
        // déplace l'objet dans la corbeille
        mMbox.move(mOpCtxt, mItem.getId(), MailItem.Type.UNKNOWN, Mailbox.ID_FOLDER_TRASH);

    }

    public void notifyFolders() throws ServiceException {
        notifyFolders(getFolder());
    }

    public void notifyFolders(Folder folder) {

        if (folder == null) {
            return;
        }

        int id = -1;
        int limit = 30;

        try {
            while (id != 1) {

                ZimbraLog.extensions.info("Notify folder " + folder.getId());

                MailItem.CustomMetadata custom = new MailItem.CustomMetadata("oc");
                String oc = RandomStringUtils.randomAscii(8);
                custom.put("ocid", oc);
                mMbox.setCustomData(mOpCtxt, folder.getId(), MailItem.Type.UNKNOWN, custom);

                folder = (Folder) folder.getParent();
                id = folder.getId();

                limit = limit - 1;
                if (limit < 0) {
                    throw ServiceException.INTERRUPTED("Cannot find root folder for " + folder.getName());
                }
            }

        } catch (ServiceException e) {
            ZimbraLog.extensions.error("Error notify folder " + folder.getPath());
        }
    }

}
