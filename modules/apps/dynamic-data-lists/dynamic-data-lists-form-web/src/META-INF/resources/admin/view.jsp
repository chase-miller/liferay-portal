<%--
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
--%>

<%@ include file="/admin/init.jsp" %>

<%
PortletURL portletURL = ddlFormAdminDisplayContext.getPortletURL();
%>

<aui:form action="<%= portletURL.toString() %>" method="post" name="searchContainerForm">
	<aui:input name="redirect" type="hidden" value="<%= portletURL.toString() %>" />
	<aui:input name="deleteStructureIds" type="hidden" />

	<liferay-ui:search-container
		emptyResultsMessage="no-forms-were-found"
		id="searchContainer"
		searchContainer="<%= new RecordSetSearch(renderRequest, portletURL) %>"
	>

		<%
		request.setAttribute(WebKeys.SEARCH_CONTAINER, searchContainer);
		%>

		<liferay-util:include page="/admin/toolbar.jsp" servletContext="<%= application %>" />

		<liferay-ui:search-container-results
			results="<%= ddlFormAdminDisplayContext.getSearchContainerResults(searchContainer) %>"
			total="<%= ddlFormAdminDisplayContext.getSearchContainerTotal(searchContainer) %>"
		/>

		<liferay-ui:search-container-row
			className="com.liferay.dynamic.data.lists.model.DDLRecordSet"
			escapedModel="<%= true %>"
			keyProperty="recordSetId"
			modelVar="recordSet"
		>
			<portlet:renderURL var="rowURL">
				<portlet:param name="mvcPath" value="/admin/edit_record_set.jsp" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
				<portlet:param name="recordSetId" value="<%= String.valueOf(recordSet.getRecordSetId()) %>" />
			</portlet:renderURL>

			<liferay-ui:search-container-column-text
				href="<%= rowURL %>"
				name="name"
				value="<%= recordSet.getName(locale) %>"
			/>

			<liferay-ui:search-container-column-text
				href="<%= rowURL %>"
				name="description"
				value="<%= StringUtil.shorten(recordSet.getDescription(locale), 100) %>"
			/>

			<liferay-ui:search-container-column-date
				href="<%= rowURL %>"
				name="modified-date"
				value="<%= recordSet.getModifiedDate() %>"
			/>

			<liferay-ui:search-container-column-jsp
				align="right"
				cssClass="entry-action"
				path="/admin/record_set_action.jsp"
			/>
		</liferay-ui:search-container-row>

		<liferay-ui:search-iterator />
	</liferay-ui:search-container>
</aui:form>