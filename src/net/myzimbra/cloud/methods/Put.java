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

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.FileUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.service.FileUploadServlet;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Put action
 *
 * @author Laurent FRANCOISE
 */
public class Put extends CloudMethod {

    private File mCloudTmpDir;
    private static final Pattern mChunkPattern = Pattern.compile("(.*)-chunking-(\\d+)-(\\d+)-(\\d+)");

    private String mChunkName, mChunkChecksum;
    private int mChunkCount, mChunkNumber;

    public Put(Account account, String path) throws ServiceException {
        super(account, path);
    }

    public void uploadChunk(InputStream in) throws IOException {

        // /opt/zimbra/data/tmp/cloud/
        if (mCloudTmpDir == null) {
            try {
                File tmpDir = new File(LC.zimbra_tmp_directory.value());
                mCloudTmpDir = new File(tmpDir, "cloud");
                FileUtil.ensureDirExists(mCloudTmpDir);

            } catch (IOException e) {
                ZimbraLog.extensions.error(e);
            }

        }

        String accountId = mAccount.getId();
        String hash = accountId.substring(0, 2);

        // /opt/zimbra/data/tmp/cloud/<hash>/
        File hashTmpDir = new File(mCloudTmpDir, hash);
        FileUtil.ensureDirExists(hashTmpDir);

        // /opt/zimbra/data/tmp/cloud/<hash>/<account-id>/
        File accountTmpDir = new File(hashTmpDir, accountId);
        FileUtil.ensureDirExists(accountTmpDir);

        // /opt/zimbra/data/tmp/cloud/<hash>/account-id>/<filename>-chunking-<checksum>-<count>-<number>
        String name = new File(mPath).getName();
        File tmpFile = new File(accountTmpDir, name);
        FileUtil.copy(in, true, tmpFile);

    }

    private void parseChunkFilename(String chunkFilename) {
        Matcher m = mChunkPattern.matcher(chunkFilename);

        boolean b = m.matches();

        if (b) {
            mChunkName = m.group(1);
            mChunkChecksum = m.group(2);
            mChunkCount = Integer.parseInt(m.group(3));
            mChunkNumber = Integer.parseInt(m.group(4));
        }
        // TODO : b==false
    }

    public boolean isLastChunk() {
        String name = new File(mPath).getName();
        parseChunkFilename(name);
        ZimbraLog.extensions.info("ISLASTCHUNK " + mPath + " count=" + mChunkCount + ", number=" + mChunkNumber);
        return (mChunkCount == (mChunkNumber + 1));
    }

    public void finishUploadChunk(String ctype) throws IOException, ServiceException {

        ArrayList<InputStream> ins = new ArrayList<>();
        ArrayList<File> tmpFiles = new ArrayList<>();

        String name = new File(mPath).getName();
        parseChunkFilename(name);

        // /opt/zimbra/data/tmp/cloud/<hash>/<account_id>
        String accountId = mAccount.getId();
        String hash = accountId.substring(0, 2);
        File hashTmpDir = new File(mCloudTmpDir, hash);
        File accountTmpDir = new File(hashTmpDir, accountId);

        for (int i = 0; i < mChunkCount; i++) {
            String chunkName = mChunkName + "-chunking-" + mChunkChecksum + "-" + String.valueOf(mChunkCount) + "-" + String.valueOf(i);
            File tmpFile = new File(accountTmpDir, chunkName);
            ZimbraLog.extensions.info("Adding " + tmpFile.getAbsolutePath());
            ins.add(new FileInputStream(tmpFile));
            tmpFiles.add(tmpFile);
        }

        Enumeration<InputStream> enu = Collections.enumeration(ins);
        SequenceInputStream sequence = new SequenceInputStream(enu);

        // update mItem
        String path = new File(mPath).getParent();
        mPath = path + "/" + mChunkName;
        findItem();

        /*try {*/
        upload(sequence, ctype);
        /* } catch (IOException | ServiceException ex) {
         ZimbraLog.extensions.error(ex);
         } finally {*/
        // Remove temp files
        for (File tmpFile : tmpFiles) {
            deleteTmpFile(tmpFile);
        }

        // Cleanup old files
        cleanup(accountTmpDir);
        /* }*/
    }

    private void deleteTmpFile(File tmpFile) {
        Matcher m = mChunkPattern.matcher(tmpFile.getName());
        boolean b = m.matches();
        if (b) {
            try {
                FileUtil.delete(tmpFile);
            } catch (IOException ex) {
                ZimbraLog.extensions.error("Cannot delete tmp file " + tmpFile.getAbsolutePath());
            }
        } else {
            ZimbraLog.extensions.error("File " + tmpFile.getAbsolutePath() + " is not a tmp chunking file");
        }
    }

    private void cleanup(File accountTmpDir) {

        long now = System.currentTimeMillis();

        for (File tmpFile : accountTmpDir.listFiles()) {
            // file older than 30mn (1800*1000)
            if ((now - tmpFile.lastModified()) > 1800000) {
                deleteTmpFile(tmpFile);
                ZimbraLog.extensions.warn("Deleting old tmp File " + tmpFile.getAbsolutePath());
            }
        }

    }

    public void upload(InputStream in, String ctype) /*throws IOException, ServiceException*/ {

        Document doc = null;

        FileUploadServlet.Upload upload = null;

        try {
            // cf zimbra source code : public DavResource createItem(DavContext ctxt, String name)
            upload = FileUploadServlet.saveUpload(in, mPath, ctype, mAccount.getId(), true);

            String author = mAccount.getName();

            File fileItem = new File(mPath);
            String folder = fileItem.getParent();
            String name = fileItem.getName();

            // if the document exists, add a revision
            if (mItem != null) {
                doc = mMbox.addDocumentRevision(mOpCtxt, mItem.getId(), author, name, null, upload.getInputStream());
            } else {
                // otherwise creates the document
                MailItem parent = mMbox.getItemByPath(mOpCtxt, folder);

                doc = mMbox.createDocument(mOpCtxt, parent.getId(), name, upload.getContentType(), author, null, upload.getInputStream());
            }

        } catch (IOException | ServiceException e) {
            ZimbraLog.extensions.error("PUT upload :" + e);
        } finally {
            if (upload != null) {
                FileUploadServlet.deleteUpload(upload);
            }
        }

        // update the item
        mItem = doc;
    }

}
