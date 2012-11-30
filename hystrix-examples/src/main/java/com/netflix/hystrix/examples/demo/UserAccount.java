/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.examples.demo;

/**
 * Simple POJO to represent a user and their metadata.
 */
public class UserAccount {

    private final int userId;
    private final String name;
    private final int accountType;
    private final boolean isFeatureXenabled;
    private final boolean isFeatureYenabled;
    private final boolean isFeatureZenabled;

    public UserAccount(int userId, String name, int accountType, boolean x, boolean y, boolean z) {
        this.userId = userId;
        this.name = name;
        this.accountType = accountType;
        this.isFeatureXenabled = x;
        this.isFeatureYenabled = y;
        this.isFeatureZenabled = z;
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getAccountType() {
        return accountType;
    }

    public boolean isFeatureXenabled() {
        return isFeatureXenabled;
    }

    public boolean isFeatureYenabled() {
        return isFeatureYenabled;
    }

    public boolean isFeatureZenabled() {
        return isFeatureZenabled;
    }

}
