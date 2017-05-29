package org.adempiere.model.copyRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.copyRecord.CopyRecordSupport.IOnRecordCopiedListener;
import org.adempiere.service.ISysConfigBL;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.compiere.model.PO;
import org.slf4j.Logger;

import de.metas.logging.LogManager;
import lombok.Builder;
import lombok.NonNull;

/**
 * {@link CopyRecordSupport} factory.
 *
 * @author Cristina Ghita, METAS.RO
 */
@Builder
public class CopyRecordFactory
{
	private static final transient Logger logger = LogManager.getLogger(CopyRecordFactory.class);

	private static final String SYSCONFIG_ENABLE_COPY_WITH_DETAILS = "ENABLE_COPY_WITH_DETAILS";

	/** DynAttr which holds the <code>CopyRecordSupport</code> class which handles this PO */
	static final String DYNATTR_CopyRecordSupport = "CopyRecordSupport";
	/** DynAttr which holds the source PO from which this PO was copied */
	private static final String DYNATTR_CopyRecordSupport_OldValue = "CopyRecordSupportOldValue";

	private static final Map<String, Class<? extends CopyRecordSupport>> tableName2copyRecordSupportClass = new ConcurrentHashMap<>();

	/** List of table names for whom Copy With Details button is activated in Window toolbar */
	private static final Set<String> enabledTableNames = new CopyOnWriteArraySet<>();

	private static final List<IOnRecordCopiedListener> staticOnRecordCopiedListeners = new CopyOnWriteArrayList<>();

	public static void registerCopyRecordSupport(final String tableName, final Class<? extends CopyRecordSupport> copyRecordSupportClass)
	{
		Check.assumeNotEmpty(tableName, "tableName not empty");
		Check.assumeNotNull(copyRecordSupportClass, "copyRecordSupportClass not null");
		tableName2copyRecordSupportClass.put(tableName, copyRecordSupportClass);
	}

	/**
	 * @return true if copy-with-details functionality is enabled
	 */
	public static boolean isEnabled()
	{
		final boolean copyWithDetailsEnabledDefault = false;
		return Services.get(ISysConfigBL.class).getBooleanValue(SYSCONFIG_ENABLE_COPY_WITH_DETAILS, copyWithDetailsEnabledDefault);
	}

	/**
	 * @return true if copy-with-details functionality is enabled for given <code>tableName</code>
	 */
	public static boolean isEnabledForTableName(final String tableName)
	{
		return enabledTableNames.contains(tableName);
	}

	public static void enableForTableName(final String tableName)
	{
		Check.assumeNotEmpty(tableName, "tableName not empty");
		enabledTableNames.add(tableName);
		logger.info("Enabled for table: {}", tableName);
	}

	/**
	 * Allows other modules to install customer code to be executed each time a record was copied.
	 * Add a listener here, and it will automatically be added to each {@link CopyRecordSupport} instance that is returned by {@link #getCopyRecordSupport(String)}.
	 *
	 * @param listener
	 */
	public static void addOnRecordCopiedListener(@NonNull final IOnRecordCopiedListener listener)
	{
		staticOnRecordCopiedListeners.add(listener);
		logger.info("Registered listener: {}", listener);
	}

	public static CopyRecordSupport getExistingOrNull(final PO po)
	{
		final CopyRecordSupport crs = (CopyRecordSupport)po.getDynAttribute(DYNATTR_CopyRecordSupport);
		return crs;
	}

	public static int getOldPOId(final PO newPO)
	{
		final Integer oldPOId = (Integer)newPO.getDynAttribute(DYNATTR_CopyRecordSupport_OldValue);
		return oldPOId;
	}

	public static void setOldPOId(final PO newPO, final int oldPOId)
	{
		newPO.setDynAttribute(DYNATTR_CopyRecordSupport_OldValue, oldPOId);
	}

	public static void uninstall(final PO po)
	{
		po.setDynAttribute(DYNATTR_CopyRecordSupport, null);
		po.setDynAttribute(DYNATTR_CopyRecordSupport_OldValue, null);
	}

	private @NonNull final String tableName;
	private final String parentKeyColumn;
	private final List<CopyRecordSupportChildInfo> childrenInfo;

	// Needed for getValueToCopy:
	@Builder.Default
	private final int adWindowId = -1;
	private final PO parentPO;

	public CopyRecordSupport create()
	{
		final CopyRecordSupport result;
		final Class<? extends CopyRecordSupport> copyRecordSupportClass = tableName2copyRecordSupportClass.get(tableName);
		if (copyRecordSupportClass == null)
		{
			result = new GeneralCopyRecordSupport();
		}
		else
		{
			try
			{
				result = copyRecordSupportClass.newInstance();
			}
			catch (final Exception ex)
			{
				throw new AdempiereException("Failed creating " + CopyRecordSupport.class + " instance for " + tableName, ex);
			}
		}

		for (final IOnRecordCopiedListener listener : staticOnRecordCopiedListeners)
		{
			result.addOnRecordCopiedListener(listener);
		}

		result.setChildrenInfo(childrenInfo);
		result.setParentKeyColumn(parentKeyColumn);

		// Needed for getValueToCopy:
		result.setAD_Window_ID(adWindowId);
		result.setParentPO(parentPO);

		return result;
	}

	public CopyRecordSupport createAndInstall(final PO newRootPO, final int oldRootId)
	{
		final CopyRecordSupport copier = create();
		newRootPO.setDynAttribute(DYNATTR_CopyRecordSupport, copier);
		newRootPO.setDynAttribute(DYNATTR_CopyRecordSupport_OldValue, oldRootId);

		return copier;
	}
}
