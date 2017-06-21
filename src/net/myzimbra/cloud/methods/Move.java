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

import net.myzimbra.cloud.Config;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import java.io.File;
import java.io.UnsupportedEncodingException;

/**
 * Move action
 *
 * @author Laurent FRANCOISE
 */
public class Move extends CloudMethod {

    private String mTargetPath;
    private MailItem mTargetItem;

    public Move(Account account, String path, String targetPath) throws ServiceException {
        super(account, path);
        mTargetPath = targetPath;

        // remove the URL prefix if present
        int prefixIdx = mTargetPath.indexOf(Config.URL_CLOUD_PREFIX);
        if (prefixIdx > 0) {
            mTargetPath = mTargetPath.substring(prefixIdx);
        }

        mTargetPath = mTargetPath.replace(Config.URL_CLOUD_PREFIX + "/remote.php/webdav", "/Briefcase");

        try {
            mTargetPath = pathDecode(mTargetPath);
        } catch (UnsupportedEncodingException ex) {
            ZimbraLog.extensions.error("MOVE unsupported encoding " + mTargetPath);
        }

        ZimbraLog.extensions.info("MOVE src=" + mPath + ", dst=" + mTargetPath);
    }

    public final void findTargetItem() {
        mTargetItem = null;
        try {
            mTargetItem = mMbox.getItemByPath(mOpCtxt, mTargetPath);

        } catch (ServiceException e) {
            if (!(e instanceof MailServiceException.NoSuchItemException)) {
                ZimbraLog.extensions.error(e);
                //throw new IOException();
            }
        }
    }

    public boolean targetExists() {
        return mTargetItem != null;
    }

    public boolean targetIsDocument() {
        return mTargetItem instanceof com.zimbra.cs.mailbox.Document;
    }

    public boolean targetIsFolder() {
        return mTargetItem instanceof Folder;
    }

    public boolean targetPathIsFolder() {
        return mTargetPath.charAt(mTargetPath.length() - 1) == '/';
    }

    public Folder getTargetFolder() {

        if (mTargetItem == null) {
            return null;
        }

        Folder folder;
        try {
            folder = mMbox.getFolderById(mOpCtxt, mTargetItem.getFolderId());
        } catch (ServiceException ex) {
            return null;
        }
        return folder;
    }

    public void move() throws ServiceException {

        File fileItemTarget = new File(mTargetPath);
        String targetFolder = fileItemTarget.getParent();
        String targetName = fileItemTarget.getName();

        Folder folder = mMbox.getFolderByPath(mOpCtxt, targetFolder);

        // TODO : action if the target already exists
        // TODO : action if the target is a folder
        // TODO : action if the source is a folder
        // rename
        if (mItem.getFolderId() == folder.getId()) {
            ZimbraLog.extensions.info("MOVE --> RENAME ");
            mMbox.rename(mOpCtxt, mItem.getId(), mItem.getType(), targetName);

        } else {
            // move
            ZimbraLog.extensions.info("MOVE --> MOVE ");
            mMbox.move(mOpCtxt, mItem.getId(), mItem.getType(), folder.getId());
        }

    }

}
