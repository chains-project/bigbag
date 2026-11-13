/*
 * Copyright (C) 2024 Openfire Hazelcast Plugin. All rights reserved.
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

package github.chains;

import com.hazelcast.core.Member;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapEvent;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.MemberAttributeEvent;
import com.hazelcast.core.MembershipEvent;
import com.hazelcast.core.MembershipListener;

/**
 * Test class to demonstrate Hazelcast 4.0.5 breaking changes.
 */
public class TestHazelcastClasses {
    
    public void testMethod() {
        // This would cause compilation errors in Hazelcast 4.0.5
        Member member;
        Cluster cluster;
        IMap map;
        MapEvent event;
        MaxSizeConfig maxSizeConfig;
        MemberAttributeEvent attributeEvent;
        MembershipEvent membershipEvent;
        MembershipListener listener;
    }
}