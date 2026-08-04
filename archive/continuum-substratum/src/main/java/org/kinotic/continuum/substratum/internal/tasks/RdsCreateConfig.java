/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.substratum.internal.tasks;

import java.util.List;
import java.util.Map;

/**
 *
 * Created by Navid Mitchell on 7/31/20
 */
public class RdsCreateConfig {

    private String domain;
    private String suffix;
    private String databaseName;
    private String masterUsername;
    private String masterPassword;
    private String dbInstanceClass;
    private List<String> vpcSecurityGroupIds;
    private String dbSubnetGroupName;
    private int numberOfReadReplicas = 0;
    private boolean storageEncrypted = false;
    private boolean deleteProtection = false;
    private Map<String, String> additionalSecrets;

    public String getDomain() {
        return domain;
    }

    public RdsCreateConfig setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    public String getSuffix() {
        return suffix;
    }

    public RdsCreateConfig setSuffix(String suffix) {
        this.suffix = suffix;
        return this;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public RdsCreateConfig setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public String getMasterUsername() {
        return masterUsername;
    }

    public RdsCreateConfig setMasterUsername(String masterUsername) {
        this.masterUsername = masterUsername;
        return this;
    }

    public String getMasterPassword() {
        return masterPassword;
    }

    public RdsCreateConfig setMasterPassword(String masterPassword) {
        this.masterPassword = masterPassword;
        return this;
    }

    public String getDbInstanceClass() {
        return dbInstanceClass;
    }

    public RdsCreateConfig setDbInstanceClass(String dbInstanceClass) {
        this.dbInstanceClass = dbInstanceClass;
        return this;
    }

    public List<String> getVpcSecurityGroupIds() {
        return vpcSecurityGroupIds;
    }

    public RdsCreateConfig setVpcSecurityGroupIds(List<String> vpcSecurityGroupIds) {
        this.vpcSecurityGroupIds = vpcSecurityGroupIds;
        return this;
    }

    public String getDbSubnetGroupName() {
        return dbSubnetGroupName;
    }

    public RdsCreateConfig setDbSubnetGroupName(String dbSubnetGroupName) {
        this.dbSubnetGroupName = dbSubnetGroupName;
        return this;
    }

    public int getNumberOfReadReplicas() {
        return numberOfReadReplicas;
    }

    public RdsCreateConfig setNumberOfReadReplicas(int numberOfReadReplicas) {
        this.numberOfReadReplicas = numberOfReadReplicas;
        return this;
    }

    public boolean isStorageEncrypted() {
        return storageEncrypted;
    }

    public RdsCreateConfig setStorageEncrypted(boolean storageEncrypted) {
        this.storageEncrypted = storageEncrypted;
        return this;
    }

    public boolean isDeleteProtection() {
        return deleteProtection;
    }

    public RdsCreateConfig setDeleteProtection(boolean deleteProtection) {
        this.deleteProtection = deleteProtection;
        return this;
    }

    public Map<String, String> getAdditionalSecrets() {
        return additionalSecrets;
    }

    public RdsCreateConfig setAdditionalSecrets(Map<String, String> additionalSecrets) {
        this.additionalSecrets = additionalSecrets;
        return this;
    }
}
