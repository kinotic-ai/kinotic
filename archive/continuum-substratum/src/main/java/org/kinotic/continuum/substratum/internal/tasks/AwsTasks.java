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

import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.UserIdGroupPair;

import java.util.ArrayList;
import java.util.List;

/**
 * Class of static factories to simplify working with AWS and Grind!
 *
 *
 * Created by Navid Mitchell on 3/26/20
 */
public class AwsTasks {

    public static List<IpPermission> allAccessPermissionsForSecurityGroup(String securityGroupId){
        List<IpPermission> ret = new ArrayList<>();

        ret.add(IpPermission.builder()
                            .userIdGroupPairs(UserIdGroupPair.builder().groupId(securityGroupId).build())
                            .fromPort(0)
                            .toPort(65535)
                            .ipProtocol("tcp")
                            .build());

        ret.add(IpPermission.builder()
                            .userIdGroupPairs(UserIdGroupPair.builder().groupId(securityGroupId).build())
                            .fromPort(0)
                            .toPort(65535)
                            .ipProtocol("udp")
                            .build());
        return ret;
    }

}
