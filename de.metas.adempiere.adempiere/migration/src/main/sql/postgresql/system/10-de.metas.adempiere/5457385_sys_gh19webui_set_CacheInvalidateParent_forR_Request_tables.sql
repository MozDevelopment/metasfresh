/*
-- query used to investigate:

select
', ' || childColumn.AD_Column_ID || ' -- ' as prefix
, fk.Parent_TableName
, fk.Child_TableName
, fk.Child_ColumnName
, fk.Child_ColumnEntityType
, childColumn.IsParent as Child_IsParentLink
, exists(select 1 from AD_Column ck where ck.AD_Table_ID=childTable.AD_Table_ID and ck.IsKey='Y') as Child_HasPrimaryKey
, childColumn.CacheInvalidateParent
from (
	select
		ref_TableName as Parent_TableName
		, TableName as Child_TableName
		, ColumnName as Child_ColumnName
		, ColumnName_EntityType as Child_ColumnEntityType
	from db_columns_fk
) fk
inner join AD_Table childTable on (childTable.TableName=fk.Child_TableName)
inner join AD_Column childColumn on (childColumn.AD_Table_ID=childTable.AD_Table_ID and childColumn.ColumnName=fk.Child_ColumnName)
where true
--and childColumn.IsParent='Y'
-- and Parent_TableName ilike 'R_Request'
order by fk.Parent_TableName, fk.Child_TableName, fk.Child_ColumnName
;

*/


update AD_Column set CacheInvalidateParent='Y'
where AD_Column_ID in (
13490 -- ;R_Request;R_Request;R_RequestRelated_ID;D;N;t;N
, 5449 -- ;R_Request;R_RequestAction;R_Request_ID;D;Y;t;N
, 13986 -- ;R_Request;R_RequestUpdate;R_Request_ID;D;Y;t;N
, 13712 -- ;R_Request;R_RequestUpdates;R_Request_ID;D;Y;t;N
);
