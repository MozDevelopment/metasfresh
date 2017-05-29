package org.adempiere.ad.model.util;

import java.util.HashSet;
import java.util.Properties;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import java.util.Set;

import org.adempiere.ad.persistence.IModelInternalAccessor;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;

import com.google.common.collect.ImmutableSet;

import lombok.NonNull;

public class ModelCopyHelper implements IModelCopyHelper
{
	// From/To models
	private Object _fromModel;
	private IModelInternalAccessor _fromModelAccessor;
	private Object _toModel;
	private IModelInternalAccessor _toModelAccessor;

	// Parameters
	private boolean _skipCalculatedColumns = false;

	/** List of column names which we are always skipping from copying */
	private static final Set<String> DEFAULT_ColumnNamesToSkipAlways = ImmutableSet.<String> builder()
			.add("Created")
			.add("CreatedBy")
			.add("Updated")
			.add("UpdatedBy")
			.build();

	private final Set<String> targetColumnNamesToSkip = new HashSet<>();

	private ValueToCopyExtractor calculatedValueToCopyExtractor = null;

	@Override
	public void copy()
	{
		final IModelInternalAccessor from = getFromAccessor();
		final IModelInternalAccessor to = getToAccessor();

		for (final String columnName : to.getColumnNames())
		{
			//
			// Skip the column if we shall not copy it
			if (!isCopyColumn(columnName, to, from))
			{
				continue;
			}

			//
			// Extract the value to copy
			final Object value = getValueToCopy(columnName, to, from);

			//
			// Set the value to our "to" target
			final boolean valueSet = to.setValue(columnName, value);
			if (!valueSet)
			{
				throw new AdempiereException("Could not copy value for " + columnName
						+ "\n Value: " + value
						+ "\n From: " + from
						+ "\n To: " + to);
			}
		}
	}

	@Override
	public <T> T copyToNew(final Class<T> modelClass)
	{
		final Object fromModel = getFrom();
		final Properties ctx = InterfaceWrapperHelper.getCtx(fromModel);
		final T toModel = InterfaceWrapperHelper.create(ctx, modelClass, ITrx.TRXNAME_ThreadInherited);
		setTo(toModel);
		copy();

		return toModel;
	}

	@Override
	public IModelCopyHelper setFrom(final Object fromModel)
	{
		_fromModel = fromModel;
		_fromModelAccessor = null;
		return this;
	}

	private final IModelInternalAccessor getFromAccessor()
	{
		if (_fromModelAccessor == null)
		{
			return InterfaceWrapperHelper.getModelInternalAccessor(getFrom());
		}
		return _fromModelAccessor;
	}

	private Object getFrom()
	{
		Check.assumeNotNull(_fromModel, "_fromModel not null");
		return _fromModel;
	}

	@Override
	public IModelCopyHelper setTo(final Object toModel)
	{
		_toModel = toModel;
		_toModelAccessor = null;
		return this;
	}

	private final IModelInternalAccessor getToAccessor()
	{
		if (_toModelAccessor == null)
		{
			Check.assumeNotNull(_toModel, "_toModel not null");
			_toModelAccessor = InterfaceWrapperHelper.getModelInternalAccessor(_toModel);
		}
		return _toModelAccessor;
	}

	public final boolean isSkipCalculatedColumns()
	{
		return _skipCalculatedColumns && calculatedValueToCopyExtractor == null;
	}

	@Override
	public IModelCopyHelper setSkipCalculatedColumns(final boolean skipCalculatedColumns)
	{
		_skipCalculatedColumns = skipCalculatedColumns;
		return this;
	}

	@Override
	public IModelCopyHelper addTargetColumnNameToSkip(final String columnName)
	{
		targetColumnNamesToSkip.add(columnName);
		return this;
	}

	@Override
	public IModelCopyHelper setCalculatedValueToCopyExtractor(@NonNull final ValueToCopyExtractor calculatedValueToCopyExtractor)
	{
		this.calculatedValueToCopyExtractor = calculatedValueToCopyExtractor;
		return this;
	}

	private final boolean isCopyColumn(final String columnName, final IModelInternalAccessor to, final IModelInternalAccessor from)
	{
		// Skip this column if it does not exist in our "from" model
		if (!from.hasColumnName(columnName))
		{
			return false;
		}

		// Skip columns which were advised to be skipped
		if (targetColumnNamesToSkip.contains(columnName))
		{
			return false;
		}

		// Skip virtual columns
		if (to.isVirtualColumn(columnName))
		{
			return false;
		}

		// Skip copying key columns
		if (to.isKeyColumnName(columnName))
		{
			return false;
		}

		if (DEFAULT_ColumnNamesToSkipAlways.contains(columnName))
		{
			return false;
		}

		// Skip calculated columns if asked
		if (isSkipCalculatedColumns() && to.isCalculated(columnName))
		{
			return false;
		}

		return true;
	}

	private final Object getValueToCopy(final String columnName, final IModelInternalAccessor to, final IModelInternalAccessor from)
	{
		if (to.isCalculated(columnName) && calculatedValueToCopyExtractor != null)
		{
			return calculatedValueToCopyExtractor.getValueToCopy(columnName, to, from);
		}
		
		return from.getValue(columnName, Object.class);
	}

}
