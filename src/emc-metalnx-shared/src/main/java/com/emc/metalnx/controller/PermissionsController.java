/*
 * Copyright (c) 2015-2017, Dell EMC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.emc.metalnx.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.irods.jargon.core.exception.FileNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.emc.metalnx.controller.utils.LoggedUserUtils;
import com.emc.metalnx.core.domain.entity.DataGridCollectionAndDataObject;
import com.emc.metalnx.core.domain.entity.DataGridFilePermission;
import com.emc.metalnx.core.domain.entity.DataGridGroup;
import com.emc.metalnx.core.domain.entity.DataGridGroupBookmark;
import com.emc.metalnx.core.domain.entity.DataGridGroupPermission;
import com.emc.metalnx.core.domain.entity.DataGridUser;
import com.emc.metalnx.core.domain.entity.DataGridUserBookmark;
import com.emc.metalnx.core.domain.entity.DataGridUserPermission;
import com.emc.metalnx.core.domain.entity.enums.DataGridPermType;
import com.emc.metalnx.core.domain.exceptions.DataGridConnectionRefusedException;
import com.emc.metalnx.services.interfaces.CollectionService;
import com.emc.metalnx.services.interfaces.GroupBookmarkService;
import com.emc.metalnx.services.interfaces.GroupService;
import com.emc.metalnx.services.interfaces.PermissionsService;
import com.emc.metalnx.services.interfaces.UserBookmarkService;
import com.emc.metalnx.services.interfaces.UserService;

@Controller
@SessionAttributes({ "currentPath", "groupsToAdd", "usersToAdd" })
@RequestMapping(value = "/permissions")
public class PermissionsController {

	@Autowired
	private UserService us;

	@Autowired
	private GroupService gs;

	@Autowired
	private GroupBookmarkService gBMS;

	@Autowired
	private UserBookmarkService uBMS;

	@Autowired
	private PermissionsService ps;

	@Autowired
	private LoggedUserUtils luu;

	@Autowired
	private CollectionService cs;

	private DataGridUser loggedUser;

	private HashMap<String, String> usersToAdd;
	private HashMap<String, String> groupsToAdd;

	private static final String[] PERMISSIONS = { "NONE", "READ", "WRITE", "OWN" };
	private static final String[] PERMISSIONS_WITHOUT_NONE = { "READ", "WRITE", "OWN" };

	private static final String REQUEST_OK = "OK";
	private static final String REQUEST_ERROR = "ERROR";
	private static final Logger logger = LoggerFactory.getLogger(PermissionsController.class);

	/**
	 * Finds the most restrictive permission on paths selected on the UI.
	 *
	 * @return string containing the most restrictive permission ("none", "read",
	 *         "write", or "own")
	 * @throws DataGridConnectionRefusedException
	 *             if Metalnx cannot connect to the grid
	 */
	@RequestMapping(value = "/findMostRestrictive/", method = RequestMethod.POST, produces = { "text/plain" })
	@ResponseBody
	private String findMostRestrictivePermission(@RequestParam("paths[]") final String[] paths)
			throws DataGridConnectionRefusedException {

		DataGridPermType mostRestrictivePermission = ps.findMostRestrictivePermission(paths);

		boolean isAdmin = luu.getLoggedDataGridUser().isAdmin();
		boolean isPermNone = mostRestrictivePermission.equals(DataGridPermType.NONE);

		if (isPermNone && isAdmin) {
			mostRestrictivePermission = DataGridPermType.IRODS_ADMIN;
		}

		return mostRestrictivePermission.toString().toLowerCase();
	}

	/**
	 * Gives permission details related to a collection or file that is passed as a
	 * parameter
	 *
	 * @param model
	 * @param path
	 * @return
	 * @throws DataGridConnectionRefusedException
	 * @throws JargonException
	 * @throws FileNotFoundException
	 */
	@RequestMapping(value = "/getPermissionDetails/" , method = RequestMethod.POST)
	public String getPermissionDetails(final Model model, @RequestParam("path") final String path) throws DataGridConnectionRefusedException {
		
		logger.debug("Getting permission info for {}", path);

		DataGridCollectionAndDataObject obj = null;
		List<DataGridFilePermission> permissions;
		List<DataGridGroupPermission> groupPermissions;
		List<DataGridUserPermission> userPermissions;
		List<DataGridGroupBookmark> bookmarks;
		List<DataGridUserBookmark> userBookmarks;
		Set<String> groupsWithBookmarks;
		Set<String> usersWithBookmarks;
		boolean userCanModify = false;
		boolean isCollection = false;

		try {
			loggedUser = luu.getLoggedDataGridUser();

			permissions = ps.getPathPermissionDetails(path);
			groupPermissions = ps.getGroupsWithPermissions(permissions);
			userPermissions = ps.getUsersWithPermissions(permissions);

			bookmarks = gBMS.findBookmarksOnPath(path);
			userBookmarks = uBMS.findBookmarksOnPath(path);
			userCanModify = loggedUser.isAdmin() || ps.canLoggedUserModifyPermissionOnPath(path);

			groupsWithBookmarks = new HashSet<>();
			for (DataGridGroupBookmark bookmark : bookmarks) {
				groupsWithBookmarks.add(bookmark.getGroup().getGroupname());
			}

			usersWithBookmarks = new HashSet<>();
			for (DataGridUserBookmark userBookmark : userBookmarks) {
				usersWithBookmarks.add(userBookmark.getUser().getUsername());
			}

			obj = cs.findByName(path);
			ps.resolveMostPermissiveAccessForUser(obj, loggedUser);
		} catch (Exception e) {
			logger.error("Could not get permission details {}: {}", path, e.getMessage());

			groupPermissions = new ArrayList<>();
			userPermissions = new ArrayList<>();
			groupsWithBookmarks = new HashSet<>();
			usersWithBookmarks = new HashSet<>();
		}

		model.addAttribute("usersWithBookmarks", usersWithBookmarks);
		model.addAttribute("groupsWithBookmark", groupsWithBookmarks);
		model.addAttribute("groupPermissions", groupPermissions);
		model.addAttribute("userPermissions", userPermissions);
		model.addAttribute("userCanModify", userCanModify);
		model.addAttribute("permissions", PERMISSIONS);
		model.addAttribute("permissionsWithoutNone", PERMISSIONS_WITHOUT_NONE);
		model.addAttribute("collectionAndDataObject", obj);
		model.addAttribute("isCollection", isCollection);
		model.addAttribute("permissionOnCurrentPath", cs.getPermissionsForPath(path));
		model.addAttribute("permissionFlag", true);
		
		System.out.println("permissionOnCurrentPath =======" + cs.getPermissionsForPath(path));
		System.out.println("------Permission Conroller - /getPermissionDetail/ ends------");
		return "permissions/permissionDetails :: permissionDetails";		
	}

	@RequestMapping(value = "/changePermissionForGroup/")
	@ResponseBody
	public String changePermisionForGroup(@RequestParam("permissionData") final String permissionData,
			@RequestParam("recursive") final boolean recursive)
			throws DataGridConnectionRefusedException, JargonException {
		return changePermissionForUserOrGroupOnPath(permissionData, recursive);
	}

	@RequestMapping(value = "/changePermissionForUser/")
	@ResponseBody
	public String changePermisionForUser(@RequestParam("permissionData") final String permissionData,
			@RequestParam("recursive") final boolean recursive)
			throws DataGridConnectionRefusedException, JargonException {
		return changePermissionForUserOrGroupOnPath(permissionData, recursive);
	}

	/**
	 * Renders table that allows client to select which groups he wants to set new
	 * permissions to.
	 *
	 * @return
	 */
	@RequestMapping(value = "/getListOfGroupsForPermissionsCreation/")
	public String getListOfGroupsForPermissionsCreation(final Model model) {
		List<DataGridGroup> groups = gs.findAll();

		model.addAttribute("groups", groups);
		model.addAttribute("groupsToAdd", groupsToAdd);
		model.addAttribute("permissions", PERMISSIONS_WITHOUT_NONE);

		return "permissions/groupsForPermissionCreation";
	}

	/**
	 * Renders table that allows client to select which users he wants to set new
	 * permissions to.
	 *
	 * @return
	 */
	@RequestMapping(value = "/getListOfUsersForPermissionsCreation/")
	public String getListOfUsersForPermissionsCreation(final Model model) {
		List<DataGridUser> users = us.findAll();

		model.addAttribute("users", users);
		model.addAttribute("usersToAdd", usersToAdd);
		model.addAttribute("permissions", PERMISSIONS_WITHOUT_NONE);

		return "permissions/usersForPermissionCreation";
	}

	@RequestMapping(value = "/addGroupPermissions/")
	@ResponseBody
	public String addGroupToCreationList(@RequestParam("permission") final String permission,
			@RequestParam("groups") final String groups, @RequestParam("path") final String path,
			@RequestParam("bookmark") final boolean bookmark, @RequestParam("recursive") final boolean recursive)
			throws DataGridConnectionRefusedException {

		boolean operationResult = true;
		String[] groupParts = groups.split(",");
		DataGridPermType permType = DataGridPermType.valueOf(permission);

		loggedUser = luu.getLoggedDataGridUser();

		for (String group : groupParts) {
			if (gs.findByGroupname(group).isEmpty()) {
				return REQUEST_ERROR;
			}
		}
		for (String group : groupParts) {
			operationResult &= ps.setPermissionOnPath(permType, group, recursive, loggedUser.isAdmin(), path);
		}

		// Updating bookmarks for the recently-created permissions
		if (bookmark) {
			Set<String> bookmarks = new HashSet<String>();
			bookmarks.add(path);

			// Getting list of groups and updating bookmarks
			List<DataGridGroup> groupObjects = gs.findByGroupNameList(groupParts);
			for (DataGridGroup g : groupObjects) {
				gBMS.updateBookmarks(g, bookmarks, null);
			}
		}

		return operationResult ? REQUEST_OK : REQUEST_ERROR;
	}

	@RequestMapping(value = "/addUserPermissions/")
	@ResponseBody
	public String addUserToCreationList(@RequestParam("permission") final String permission,
			@RequestParam("users") final String users, @RequestParam("path") final String path,
			@RequestParam("bookmark") final boolean bookmark, @RequestParam("recursive") final boolean recursive)
			throws DataGridConnectionRefusedException {

		boolean operationResult = true;
		String[] usernames = users.split(",");
		DataGridPermType permType = DataGridPermType.valueOf(permission);

		loggedUser = luu.getLoggedDataGridUser();

		for (String username : usernames) {
			if (us.findByUsername(username).isEmpty()) {
				return REQUEST_ERROR;
			}
		}

		for (String username : usernames) {
			operationResult &= ps.setPermissionOnPath(permType, username, recursive, loggedUser.isAdmin(), path);

			// Updating bookmarks for the recently-created permissions
			if (bookmark) {
				Set<String> bookmarks = new HashSet<String>();
				bookmarks.add(path);

				// Getting list of users and updating bookmarks
				List<DataGridUser> dataGridUsers = us.findByUsername(username);
				if (dataGridUsers != null && !dataGridUsers.isEmpty()) {
					uBMS.updateBookmarks(dataGridUsers.get(0), bookmarks, null);
				}
			}
		}

		return operationResult ? REQUEST_OK : REQUEST_ERROR;

	}

	/* ********************************************************************* */
	/* **************************** PRIVATE METHOS ************************* */
	/* ********************************************************************* */

	/**
	 * Sends the permissions change to the services layer and returns the result of
	 * the operation.
	 *
	 * @param permissionData
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 * @throws DataGridConnectionRefusedException
	 */
	private String changePermissionForUserOrGroupOnPath(final String permissionData, final boolean recursive)
			throws DataGridConnectionRefusedException {

		// Getting information about the new permission to be applied and the path
		// of the current object (collection or data object)
		String[] permissionParts = permissionData.split("#");
		DataGridPermType newPermission = DataGridPermType.valueOf(permissionParts[0]);
		String path = permissionParts[1];
		String userOrGroupName = permissionParts[2];

		loggedUser = luu.getLoggedDataGridUser();

		boolean permChanged = ps.setPermissionOnPath(newPermission, userOrGroupName, recursive, loggedUser.isAdmin(),
				path);

		return permChanged ? REQUEST_OK : REQUEST_ERROR;
	}

}
