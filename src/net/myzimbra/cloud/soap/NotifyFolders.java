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
package net.myzimbra.cloud.soap;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailItem.CustomMetadata;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.soap.DocumentHandler;
import static com.zimbra.soap.DocumentHandler.getOperationContext;
import static com.zimbra.soap.DocumentHandler.getRequestedAccount;
import static com.zimbra.soap.DocumentHandler.getRequestedMailbox;
import static com.zimbra.soap.DocumentHandler.getZimbraSoapContext;
import com.zimbra.soap.ZimbraSoapContext;
import java.util.Map;
import org.apache.commons.lang.RandomStringUtils;
import org.dom4j.Namespace;
import org.dom4j.QName;

/**
 *
 * cf cs/service/mail/SetCustomMetadata.java
 *
 * @author Laurent FRANCOISE
 */
public class NotifyFolders extends DocumentHandler {

    static QName REQUEST_QNAME = new QName("NotifyFoldersRequest", Namespace.get("urn:zimbraCloud"));
    static QName RESPONSE_QNAME = new QName("NotifyFoldersResponse", Namespace.get("urn:zimbraCloud"));

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Account account = getRequestedAccount(zsc);
        Mailbox mbox = getRequestedMailbox(zsc);
        OperationContext octxt = getOperationContext(zsc, context);

        if (!canAccessAccount(zsc, account)) {
            throw ServiceException.PERM_DENIED("can not access account");
        }

        Folder folder;

        Element pathElt = request.getOptionalElement("path");
        if (pathElt != null) {
            String path = pathElt.getText();
            folder = mbox.getFolderByPath(octxt, path);

            if (folder == null) {
                throw ServiceException.NOT_FOUND("folder" + path + " not found");
            }

        } else {

            Element folderIdElt = request.getElement("id");
            String folderId = folderIdElt.getTextTrim();
            folder = mbox.getFolderById(octxt, Integer.valueOf(folderId));

            if (folder == null) {
                throw ServiceException.NOT_FOUND("folder id " + folderId + " not found");
            }

        }

        if (folder.getDefaultView() != MailItem.Type.DOCUMENT) {
            throw ServiceException.NOT_FOUND("folder " + folder.getName() + " (" + folder.getId() + ") is not in Document view.");
        }

        int id = -1;
        int limit = 30;

        String ids = "";

        while (id != 1) {

            ids = ids + " " + folder.getId();

            CustomMetadata custom = new CustomMetadata("oc");
            String oc = RandomStringUtils.randomAscii(8);
            custom.put("ocid", oc);
            mbox.setCustomData(octxt, folder.getId(), MailItem.Type.UNKNOWN, custom);

            folder = (Folder) folder.getParent();
            id = folder.getId();

            limit = limit - 1;
            if (limit < 0) {
                throw ServiceException.INTERRUPTED("Cannot find root folder for " + folder.getName());
            }
        }

        Element response = zsc.createElement(RESPONSE_QNAME);
        Element replyElt = response.addElement("reply");

        replyElt.setText("Folder " + ids + ", account " + account.getMail() + "!");
        return response;

    }

    /* @Override
    public boolean needsAuth(Map<String, Object> context) {
        return true;
    }*/
}
