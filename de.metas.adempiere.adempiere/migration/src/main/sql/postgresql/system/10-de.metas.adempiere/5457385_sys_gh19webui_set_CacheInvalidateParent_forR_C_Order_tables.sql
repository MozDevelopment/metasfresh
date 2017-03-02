/*
-- query used to investigate:

select
', ' || childColumn.AD_Column_ID || ' -- ' as prefix
, fk.Parent_TableName
, fk.Child_TableName
, fk.Child_ColumnName
, fk.Child_ColumnEntityType
, childColumn.IsParent as Child_IsParentLink
, childTable.IsView as Child_IsView
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
and childTable.IsView='N'
and Parent_TableName ilike 'C_Order'
and Child_TableName like Parent_TableName||'%'
order by Child_ColumnEntityType, fk.Parent_TableName, fk.Child_TableName, fk.Child_ColumnName
;


*/


update AD_Column set CacheInvalidateParent='Y'
where AD_Column_ID in (
 2213 -- ;C_Order;C_OrderLine;C_Order_ID;D;Y;N;t;N
, 3355 -- ;C_Order;C_OrderTax;C_Order_ID;D;Y;N;t;N
);
