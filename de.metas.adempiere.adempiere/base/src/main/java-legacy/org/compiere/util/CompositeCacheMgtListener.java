package org.compiere.util;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.adempiere.util.Check;
import org.slf4j.Logger;

import de.metas.logging.LogManager;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
 * %%
 * Copyright (C) 2017 metas GmbH
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

/* package */final class CompositeCacheMgtListener implements CacheMgtListener
{
	private static final Logger logger = LogManager.getLogger(CompositeCacheMgtListener.class);

	private final ConcurrentLinkedQueue<CacheMgtListener> listeners = new ConcurrentLinkedQueue<>();

	public void addListener(final CacheMgtListener listener)
	{
		Check.assumeNotNull(listener, "Parameter listener is not null");
		listeners.add(listener);
	}

	@Override
	public void onReset(final String tableName, final int recordId)
	{
		listeners.forEach(listener -> {
			try
			{
				listener.onReset(tableName, recordId);
			}
			catch (Exception ex)
			{
				logger.warn("Failed invoking {} for tableName={}, recordId={}. Ignored", listener, tableName, recordId, ex);
			}
		});
	}
}
