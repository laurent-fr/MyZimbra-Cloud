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

import com.zimbra.soap.DocumentDispatcher;
import com.zimbra.soap.DocumentService;

/**
 * Defines a new Zimbra SOAP call : update the etag of parents folders where
 * needed
 *
 * This is called by the Zimlet which extends the briefcase
 *
 * @author Laurent FRANCOISE
 */
public class CloudExtnService implements DocumentService {

    @Override
    public void registerHandlers(DocumentDispatcher dispatcher) {
        dispatcher.registerHandler(NotifyFolders.REQUEST_QNAME, new NotifyFolders());

    }

}
