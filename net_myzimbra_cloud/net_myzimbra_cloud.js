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

function net_myzimbra_cloud_HandlerObject() {
}

net_myzimbra_cloud_HandlerObject.prototype = new ZmZimletBase();
net_myzimbra_cloud_HandlerObject.prototype.constructor = net_myzimbra_cloud_HandlerObject;

// ***************************************************************************
// INIT
// ***************************************************************************

net_myzimbra_cloud_HandlerObject.prototype.init = function () {

    this.briefcasePatch = false;
    this.addButton = false;

    this.renameApp(); // <- Comment this line if you do not want to rename the 'Briefcase' app to 'Cloud'

    // patch the briefcase app once loaded
    var myzimbracloud = this;
    AjxDispatcher.addPackageLoadFunction("Briefcase", function() {
        myzimbracloud.applyBriefcasePatch();
    });

    console.log("MyZimbra Cloud init.");

};


// ***************************************************************************
// The Briefcase folder is renamed 'Cloud'
// ***************************************************************************

net_myzimbra_cloud_HandlerObject.prototype.renameApp = function() {
    var t = document.getElementById("zb__App__Briefcase_title");
    t.innerHTML = "Cloud";
};


// ***************************************************************************
// Patch add/modify/delete/rename actions for files/folders
// calls NotifyFolderRequest 
// ***************************************************************************

net_myzimbra_cloud_HandlerObject.prototype.applyBriefcasePatch = function() {

    var myzimbracloud = this;
    var bc = AjxDispatcher.run("GetBriefcaseController");

    // SEND MAIL TO BRIEFCASE
    ZmBriefcaseBaseItem.prototype._handleResponseCreateItem_orig = ZmBriefcaseBaseItem.prototype._handleResponseCreateItem;
    ZmBriefcaseBaseItem.prototype._handleResponseCreateItem = function(folderId, callback, response) {
        console.log("Mail2Briefcase.");
        var output = this._handleResponseCreateItem_orig(folderId, callback, response);
        console.log(folderId);
        if (myzimbracloud._isInBriefcase(folderId)) {
          myzimbracloud.notifyFolder(folderId);
        }
        return output;
    }


    // FILE MOVE 
    ZmBriefcaseController.prototype._doMove_orig = ZmBriefcaseController.prototype._doMove;
    //  ZmBriefcaseController.prototype._doMove_orig = bc._doMove; 
    ZmBriefcaseController.prototype._doMove = function(items, folder, attrs, isShiftKey, noUndo) {

        console.log("File move.");
        
        var output = this._doMove_orig(items, folder, attrs, isShiftKey, noUndo);

        var ids = [];

        // source (3=trash)
        if (items instanceof Array) {
          items.forEach(function(item) {
              var folderId = item.getFolderId();
              if (folderId == 3) return;
              if (!ids.indexOf(folderId) >= 0) ids.push(folderId);
          });
        } else 
          ids.push(items.getFolderId());


        // destination ( 3=trash)
        if (folder.id != "3") {
            if (!ids.indexOf(folder.id) >= 0) ids.push(folder.id);
        }

        ids.forEach(function(id) {
            if (myzimbracloud._isInBriefcase(id)) myzimbracloud.notifyFolder(id);
        });


        return output;
    }

    // FILE DELETE
    ZmBriefcaseController.prototype._doDelete2_orig = ZmBriefcaseController.prototype._doDelete2;

    ZmBriefcaseController.prototype._doDelete2 = function(items) {

        console.log("File delete.");

        var output = this._doDelete2_orig(items);

        var ids = [];
        items.forEach(function(item) {
            var folderId = item.getFolderId();
            if (folderId == 3) return;
            if (!ids.indexOf(folderId) >= 0) ids.push(folderId);
        });

        ids.forEach(function(id) {
           if (myzimbracloud._isInBriefcase(id)) myzimbracloud.notifyFolder(id);
        });

        return output;
    }

    // FILE PUT
    ZmBriefcaseApp.prototype._handlePostUpload_orig = ZmBriefcaseApp.prototype._handlePostUpload;
    ZmBriefcaseController.prototype._handlePostUpload = function(folder, filenames, files) {
        console.log("File put.");
       // var output = this._handlePostUpload_orig(folder, filenames, files);

        if (myzimbracloud._isInBriefcase(folder)) myzimbracloud.notifyFolder(folder.id);

        //return output;
    }

    // FILE RENAME
    ZmItem.prototype.rename_orig = ZmItem.prototype.rename;

    ZmItem.prototype.rename = function(newName, callback, errorCallback) {
        console.log("File rename.");
        var output = this.rename_orig(newName, callback, errorCallback);

        if (this instanceof ZmBriefcaseItem) {

            if (myzimbracloud._isInBriefcase(this.folderId))  myzimbracloud.notifyFolder(this.folderId);
        }

        return output;
    }


    // FOLDER CREATE
    ZmOrganizer.create_orig = ZmOrganizer.create;

    ZmOrganizer.create = function(params) {

        console.log("Folder create.");
        var output = ZmOrganizer.create_orig(params);
        console.log(params.l);
        if (myzimbracloud._isInBriefcase(params.l)) myzimbracloud.notifyFolder(params.l);
        return output;
    }

    // FOLDER MOVE + DELETE
    ZmOrganizer.prototype.move_orig = ZmOrganizer.prototype.move;

    ZmOrganizer.prototype.move = function(newParent, noUndo, actionText, batchCmd, organizerName) {

        console.log("Folder move.");
        var isInBriefcase = myzimbracloud._isInBriefcase(this) || myzimbracloud._isInBriefcase(newParent);
        var srcFolder = this.id;
        var srcParentFolder = this.parent.id;
        var dstFolder = newParent.nId;

        var output = this.move_orig(newParent, noUndo, actionText, batchCmd, organizerName);

        if (isInBriefcase == true) {
            if (srcFolder != ZmOrganizer.ID_TRASH) {
              if (myzimbracloud._isInBriefcase(srcFolder)) myzimbracloud.notifyFolder(srcFolder);
            }
            if (dstFolder != ZmOrganizer.ID_TRASH) {
              if (myzimbracloud._isInBriefcase(dstFolder)) myzimbracloud.notifyFolder(dstFolder);
            } else {
              if (myzimbracloud._isInBriefcase(srcParentFolder)) myzimbracloud.notifyFolder(srcParentFolder);
            }
        }

        return output;
    }


    // FOLDER RENAME
    ZmOrganizer.prototype.rename_orig = ZmOrganizer.prototype.rename;
    ZmOrganizer.prototype.rename = function(name, callback, errorCallback, batchCmd) {

        console.log("Folder rename.");
        var isInBriefcase = myzimbracloud._isInBriefcase(this);
        var output = this.rename_orig(name, callback, errorCallback, batchCmd);
        if (isInBriefcase) myzimbracloud.notifyFolder(this.id);

        return output;
    }

    console.log("Briefcase Patch OK");

    this.briefcasePatch = true;
};


net_myzimbra_cloud_HandlerObject.prototype._isInBriefcase = function(folder) {
    if (! (folder instanceof ZmBriefcase) ) {
      folder = appCtxt.getById(folder);
    }
    if (! (folder instanceof ZmBriefcase) ) {
        return false;
    }
    console.log("folderid=" + folder.id);
    if (folder.id == ZmOrganizer.ID_BRIEFCASE) return true;
    return folder.isChildOf(appCtxt.getById(ZmOrganizer.ID_BRIEFCASE));

};

net_myzimbra_cloud_HandlerObject.prototype.notifyFolder = function(folderId) {

    if (typeof folderId == 'undefined') return;

    console.log("Notify Folder " + folderId);

    var t = {
        NotifyFoldersRequest: {
            _jsns: "urn:zimbraCloud",
            id: {
                _content: folderId
            }
        }
    }

    var e = appCtxt.getAppController().sendRequest({
        jsonObj: t,
        //noBusyOverlay: false,
        asyncMode: true,
    });


};

