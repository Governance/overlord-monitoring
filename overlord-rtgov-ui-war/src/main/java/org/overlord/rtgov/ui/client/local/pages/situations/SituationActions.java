/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.rtgov.ui.client.local.pages.situations;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import org.jboss.errai.ui.shared.api.annotations.DataField;
import org.jboss.errai.ui.shared.api.annotations.EventHandler;
import org.jboss.errai.ui.shared.api.annotations.Templated;
import org.overlord.rtgov.ui.client.local.ClientMessages;
import org.overlord.rtgov.ui.client.local.services.NotificationService;
import org.overlord.rtgov.ui.client.local.services.SituationsRpcService;
import org.overlord.rtgov.ui.client.local.services.rpc.IRpcServiceInvocationHandler;
import org.overlord.rtgov.ui.client.local.services.rpc.IRpcServiceInvocationHandler.RpcServiceInvocationHandlerAdapter;
import org.overlord.rtgov.ui.client.local.widgets.ToggleSwitch;
import org.overlord.rtgov.ui.client.model.BatchRetryResult;
import org.overlord.rtgov.ui.client.model.NotificationBean;
import org.overlord.rtgov.ui.client.model.SituationsFilterBean;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;

/**
 * The Situations actionPanel sidebar.
 * 
 */
@Templated("/org/overlord/rtgov/ui/client/local/site/situations.html#action-sidebar")
@Dependent
public class SituationActions extends Composite {
    @Inject
    @DataField
    protected ToggleSwitch toggleFilterSwitch;
    @Inject
    @DataField
    protected Button retrySituations;
    @Inject
    protected SituationFilters filtersPanel;
    @Inject
    protected ClientMessages i18n;
    @Inject
    protected SituationsRpcService situationsService;
    @Inject
    protected NotificationService notificationService;
    @Inject
    protected IRpcServiceInvocationHandler.VoidInvocationHandler voidInvocationHandler;
    private boolean applyActionToFilteredRowsOnly = true;

    /**
     * Constructor.
     */
    public SituationActions() {
    }

    /**
     * Called after construction and injection.
     */
    @PostConstruct
    protected void postConstruct() {
        toggleFilterSwitch.addValueChangeHandler(new ValueChangeHandler<String>() {

            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                applyActionToFilteredRowsOnly = Boolean.valueOf(event.getValue());
            }
        });
    }

    /**
     * Event handler that fires when the user clicks the retry button.
     * 
     * @param event
     */
    @EventHandler("retrySituations")
    public void onRetryClick(ClickEvent event) {
        SituationsFilterBean situationsFilterBean = applyActionToFilteredRowsOnly ? filtersPanel.getValue()
                : new SituationsFilterBean();
        final NotificationBean notificationBean = notificationService.startProgressNotification(
                i18n.format("situation-details.resubmit-message-title"), //$NON-NLS-1$
                i18n.format("situation.batch-retry-message-msg")); //$NON-NLS-1$
        situationsService.resubmit(situationsFilterBean,
                new RpcServiceInvocationHandlerAdapter<BatchRetryResult>() {
                    @Override
                    public void doOnReturn(BatchRetryResult data) {
                        notificationService.completeProgressNotification(notificationBean.getUuid(),
                                i18n.format("situation-details.message-resubmitted"), //$NON-NLS-1$
                                i18n.format(
                                        "situation.batch-retry-result", data.getProcessedCount(), data.getIgnoredCount(), data.getFailedCount())); //$NON-NLS-1$
                    }

                    @Override
                    public void doOnError(Throwable error) {
                        notificationService.completeProgressNotification(notificationBean.getUuid(),
                                i18n.format("situation-details.resubmit-error"), //$NON-NLS-1$
                                error);
                    }

                    @Override
                    public void doOnComplete(RpcResult<BatchRetryResult> result) {
                    }
                });
    }

}
