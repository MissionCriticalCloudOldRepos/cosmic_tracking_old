/*
 * Copyright 2015 The Apache Software Foundation.
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
package com.cloud.hypervisor.xenserver.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.xmlrpc.XmlRpcException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;

import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.VBD;
import com.xensource.xenapi.VM;

@RunWith(PowerMockRunner.class)
public class XenServer625ResourceTest extends CitrixResourceBaseTest{

  private final Xenserver625Resource xenServer625Resource = new Xenserver625Resource();

  @Test
  public void testPatchFilePath() {
    final String patchFilePath = xenServer625Resource.getPatchFilePath();
    final String patch = "scripts/vm/hypervisor/xenserver/xenserver62/patch";

    assertEquals(patch, patchFilePath);
  }
  @Test(expected = CloudRuntimeException.class)
  @PrepareForTest(Script.class )
  public void testGetFiles(){
    testGetPathFilesExeption(xenServer625Resource);
  }
  @Test
  @PrepareForTest(Script.class )
  public void testGetFilesListReturned(){
    testGetPathFilesListReturned(xenServer625Resource);
  }

  @Test
  public void testisDeviceUsedTrue() throws Types.XenAPIException, XmlRpcException {
    final Connection conn = mock(Connection.class);
    final VM vm = mock(VM.class);
    final VBD vbd = mock(VBD.class);

    final Set<VBD> vbds = new HashSet< >();
    vbds.add(vbd);

    final String xen_userDevice = "3";
    final Long deviceId = 3L;

    when(vm.getVBDs(conn)).thenReturn(vbds);

    when(vbd.getUserdevice(conn)).thenReturn(xen_userDevice);

    assertTrue(xenServer625Resource.isDeviceUsed(conn, vm, deviceId));
  }

  @Test
  public void testisDeviceUsedFalse() throws Types.XenAPIException, XmlRpcException {
    final Connection conn = mock(Connection.class);
    final VM vm = mock(VM.class);
    final VBD vbd = mock(VBD.class);

    final Set<VBD> vbds = new HashSet< >();
    vbds.add(vbd);

    final String xen_userDevice = "3";
    final Long deviceId = 4L;

    when(vm.getVBDs(conn)).thenReturn(vbds);

    when(vbd.getUserdevice(conn)).thenReturn(xen_userDevice);

    assertFalse(xenServer625Resource.isDeviceUsed(conn, vm, deviceId));
  }

  @Test
  public void testGetVBDUserDeviceIds() throws Types.XenAPIException, XmlRpcException {
    final Connection conn = mock(Connection.class);
    final VM vm = mock(VM.class);
    final VBD vbd = mock(VBD.class);

    final Set<VBD> vbds = new HashSet< >();
    vbds.add(vbd);

    final Integer expected_deviceId = anyInt();

    when(vm.getVBDs(conn)).thenReturn(vbds);

    when(vbd.getUserdevice(conn)).thenReturn(expected_deviceId.toString());

    assert xenServer625Resource.getVBDUserDeviceIds(conn, vm).contains(expected_deviceId);
  }
}
