/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.lists.service.permission;

import com.liferay.dynamic.data.lists.model.DDLRecordSet;
import com.liferay.dynamic.data.lists.service.DDLRecordSetLocalServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.security.auth.PrincipalException;
import com.liferay.portal.security.permission.PermissionChecker;
import com.liferay.portal.util.PortletKeys;
import com.liferay.portlet.exportimport.staging.permission.StagingPermissionUtil;

/**
 * @author Marcellus Tavares
 */
public class DDLRecordSetPermission {

	public static void check(
			PermissionChecker permissionChecker, DDLRecordSet recordSet,
			String actionId)
		throws PortalException {

		if (!contains(permissionChecker, recordSet, actionId)) {
			throw new PrincipalException.MustHavePermission(
				permissionChecker, DDLRecordSet.class.getName(),
				recordSet.getRecordSetId(), actionId);
		}
	}

	public static void check(
			PermissionChecker permissionChecker, long recordSetId,
			String actionId)
		throws PortalException {

		if (!contains(permissionChecker, recordSetId, actionId)) {
			throw new PrincipalException.MustHavePermission(
				permissionChecker, DDLRecordSet.class.getName(), recordSetId,
				actionId);
		}
	}

	public static void check(
			PermissionChecker permissionChecker, long groupId,
			String recordSetKey, String actionId)
		throws PortalException {

		DDLRecordSet recordSet = DDLRecordSetLocalServiceUtil.getRecordSet(
			groupId, recordSetKey);

		check(permissionChecker, recordSet, actionId);
	}

	public static boolean contains(
		PermissionChecker permissionChecker, DDLRecordSet recordSet,
		String actionId) {

		Boolean hasPermission = StagingPermissionUtil.hasPermission(
			permissionChecker, recordSet.getGroupId(),
			DDLRecordSet.class.getName(), recordSet.getRecordSetId(),
			PortletKeys.DYNAMIC_DATA_LISTS, actionId);

		if (hasPermission != null) {
			return hasPermission.booleanValue();
		}

		if (permissionChecker.hasOwnerPermission(
				recordSet.getCompanyId(), DDLRecordSet.class.getName(),
				recordSet.getRecordSetId(), recordSet.getUserId(), actionId)) {

			return true;
		}

		return permissionChecker.hasPermission(
			recordSet.getGroupId(), DDLRecordSet.class.getName(),
			recordSet.getRecordSetId(), actionId);
	}

	public static boolean contains(
			PermissionChecker permissionChecker, long recordSetId,
			String actionId)
		throws PortalException {

		DDLRecordSet recordSet = DDLRecordSetLocalServiceUtil.getRecordSet(
			recordSetId);

		return contains(permissionChecker, recordSet, actionId);
	}

	public static boolean contains(
			PermissionChecker permissionChecker, long groupId,
			String recordSetKey, String actionId)
		throws PortalException {

		DDLRecordSet recordSet = DDLRecordSetLocalServiceUtil.getRecordSet(
			groupId, recordSetKey);

		return contains(permissionChecker, recordSet, actionId);
	}

}