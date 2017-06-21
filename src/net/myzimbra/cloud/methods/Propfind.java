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
import com.zimbra.cs.account.soap.SoapProvisioning;
import com.zimbra.cs.account.soap.SoapProvisioning.MailboxInfo;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang.time.FastDateFormat;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;

/**
 * Propfind action
 *
 * @author Laurent FRANCOISE
 */
public class Propfind extends CloudMethod {

    private String mDepth;

    private ArrayList<String> mProps;

    private Namespace mNsD, mNsS, mNsOC;

    protected static final FastDateFormat mDateFormater = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    public Propfind(Account account, String path, String depth) throws ServiceException {
        super(account, path);
        mDepth = depth;
        mProps = new ArrayList<>();
    }

    public void parse(String input) {

        // android : namespace xmlns:D="DAV:" , to lowercase
        input = input.replace("D=\"DAV:\"", "d=\"DAV:\"");
        input = input.replace("<D:", "<d:");
        input = input.replace("</D:", "</d:");

        try {

            Document document = DocumentHelper.parseText(input);

            List props = document.selectNodes("//d:prop/*");
            for (Object item : props) {
                Node prop = (Node) item;
                ZimbraLog.extensions.debug("prop: " + prop.getName());
                mProps.add(prop.getName());
            }

        } catch (DocumentException ex) {
            ZimbraLog.extensions.error(ex);
        }
    }

    public String response() throws ServiceException {
        Document xmlDocument = DocumentHelper.createDocument();

        mNsD = new Namespace("d", "DAV:");
        mNsS = new Namespace("s", "http://sabredav.org/ns");
        mNsOC = new Namespace("oc", "http://owncloud.org/ns");

        Element documentRoot = xmlDocument.addElement(new QName("multistatus", mNsD));

        documentRoot.add(mNsD);
        documentRoot.add(mNsS);
        documentRoot.add(mNsOC);

        MailItem item = mMbox.getItemByPath(mOpCtxt, mPath);

        if ((!mDepth.equals("1")) && (!mDepth.equals("0"))) {
            throw ServiceException.NOT_FOUND("depth not 0 or 1");
        }

        Folder folder = null;
        com.zimbra.cs.mailbox.Document zDocument = null;

        if (item instanceof Folder) {
            folder = (Folder) item;
        }

        if (item instanceof com.zimbra.cs.mailbox.Document) {
            zDocument = (com.zimbra.cs.mailbox.Document) item;
            folder = mMbox.getFolderById(mOpCtxt, zDocument.getFolderId());
        }

        if (folder == null) {
            throw ServiceException.NOT_FOUND("item" + mPath + " not found");
        }

        if (folder.getDefaultView() != MailItem.Type.DOCUMENT) {
            throw ServiceException.NOT_FOUND("folder " + mPath + " is not in Document view.");
        }

        if (zDocument == null) {

            ArrayList<Folder> folders;

            if (mDepth.equals("1")) {
                folders = new ArrayList<>(folder.getSubfolders(mOpCtxt));
            } else {
                folders = new ArrayList<>();

            }
            // add the root folder
            folders.add(0, folder);

            for (Folder f : folders) {
                addItem(documentRoot, f);
            }
        } // if (document==null)

        if (mDepth.equals("1") || (zDocument != null)) {

            ArrayList<com.zimbra.cs.mailbox.Document> documents;
            if (mDepth.equals("1")) {
                documents = new ArrayList<>(mMbox.getDocumentList(mOpCtxt, folder.getId()));
            } else {
                documents = new ArrayList<>();
                documents.add(zDocument);
            }

            for (com.zimbra.cs.mailbox.Document d : documents) {
                addItem(documentRoot, d);
            }
        }

        return xmlDocument.asXML();

    }

    private void addItem(Element root, MailItem item) throws ServiceException {

        //String name = item.getName();
        // if (name.equals(folder.getName())) name="";
        String etag = "";
        if (item instanceof Folder) {
            etag = String.valueOf(item.getUnderlyingData().modMetadata); //folder
        }
        if (item instanceof com.zimbra.cs.mailbox.Document) {
            etag = String.valueOf(item.getUnderlyingData().modContent); //document
        }
        long lastmodified = item.getChangeDate();

        long size = item.getSize();
        String id = item.getUuid().replace("-", "");

        String path = item.getPath();
        path = path.replace("/Briefcase", Config.URL_CLOUD_PREFIX + "/remote.php/webdav");

        // add a / if this is a folder
        if ((item instanceof Folder) && (path.charAt(path.length() - 1) != '/')) {
            path = path + "/";
        }

        Element response = root.addElement(new QName("response", mNsD));

        response.addElement(new QName("href", mNsD)).addText(path);

        Element propstat = response.addElement(new QName("propstat", mNsD));
        propstat.addElement(new QName("status", mNsD)).addText("HTTP/1.1 200 OK");
        Element prop = propstat.addElement(new QName("prop", mNsD));

        Element badpropstat = response.addElement(new QName("propstat", mNsD));
        badpropstat.addElement(new QName("status", mNsD)).addText("HTTP/1.1 404 Not Found");
        Element badprop = badpropstat.addElement(new QName("prop", mNsD));

        if (mProps.contains("getlastmodified")) {
            prop.addElement(new QName("getlastmodified", mNsD)).addText(mDateFormater.format(lastmodified));
        }

        if (mProps.contains("getcontentlength")) {
            Element getcontentlength = prop.addElement(new QName("getcontentlength", mNsD));
            if (item instanceof com.zimbra.cs.mailbox.Document) {
                getcontentlength.addText(String.valueOf(size));
            }
            if (item instanceof Folder) {
                getcontentlength.addText(String.valueOf(item.getTotalSize()));
            }
        }

        if (mProps.contains("size")) {
            Element sizeElt = prop.addElement(new QName("size", mNsOC));
            try {
                sizeElt.addText(String.valueOf(item.getTotalSize()));
            } catch (Exception ex) {
                sizeElt.addText("0");
            }
        }

        if (mProps.contains("resourcetype")) {
            Element resourcetype = prop.addElement(new QName("resourcetype", mNsD));
            if (item instanceof Folder) {
                resourcetype.addElement(new QName("collection", mNsD));
            }
        }

        if (mProps.contains("getetag")) {
            prop.addElement(new QName("getetag", mNsD)).addText(etag);
        }

        if (mProps.contains("id")) {
            prop.addElement(new QName("id", mNsOC)).addText(id);
        }

        if (mProps.contains("downloadURL")) {
            if (item instanceof Folder) {
                badprop.addElement(new QName("downloadURL", mNsOC));
            } else {
                prop.addElement(new QName("downloadURL", mNsOC));
            }
        }

        if (mProps.contains("dDC")) {
            badprop.addElement(new QName("dDC", mNsOC));
        }

        if (mProps.contains("permissions")) {
            Element permissions = prop.addElement(new QName("permissions", mNsOC));
            if (item instanceof Folder) {
                permissions.addText("RDNVCK");
            } else {
                permissions.addText("RDNVW");
            }

        }

        if (mProps.contains("quota-used-bytes")) {

            SoapProvisioning sp = SoapProvisioning.getAdminInstance();
            MailboxInfo info = sp.getMailbox(mAccount);
            String used = String.valueOf(info.getUsed());
            prop.addElement(new QName("quota-used-bytes", mNsD)).addText(used);

        }

        if (mProps.contains("quota-available-bytes")) {

            String quota = String.valueOf(mAccount.getMailQuota());
            prop.addElement(new QName("quota-available-bytes", mNsD)).addText(quota);
        }

    }

}
