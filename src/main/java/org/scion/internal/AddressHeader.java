// Copyright 2023 ETH Zurich
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.scion.internal;

import static org.scion.internal.ByteUtil.*;

import org.scion.Util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class AddressHeader {
    CommonHeader commonHeader;
    //  8 bit: DstISD
    // 48 bit: DstAS
    private long dstIsdAs;
    //  8 bit: SrcISD
    // 48 bit: SrcAS
    private long srcIsdAs;
    //  ? bit: DstHostAddr
    private int dstHost0;
    private int dstHost1;
    private int dstHost2;
    private int dstHost3;
    //  ? bit: SrcHostAddr
    private int srcHost0;
    private int srcHost1;
    private int srcHost2;
    private int srcHost3;

    private int len;

    public AddressHeader(CommonHeader commonHeader) {
        this.commonHeader = commonHeader;
    }

    public int read(byte[] data, int headerOffset) {
        //  8 bit: DstISD
        // 48 bit: DstAS
        //  8 bit: SrcISD
        // 48 bit: SrcAS
        //  ? bit: DstHostAddr
        //  ? bit: SrcHostAddr

        int offset = headerOffset;

//        long l0 = readLong(data, offset);
        dstIsdAs = readLong(data, offset);
        offset += 8;
//        long l1 = readLong(data, offset);
        srcIsdAs = readLong(data, offset);
        offset += 8;
//        dstISD = (int) readLong(l0, 0, 16);
//        dstAS = readLong(l0, 16, 48);
//        srcISD = (int) readLong(l1, 0, 16);
//        srcAS = readLong(l1, 16, 48);

        dstHost0 = readInt(data, offset);
        offset += 4;
        if (commonHeader.dl >= 1) {
            dstHost1 = readInt(data, offset);
            offset += 4;
        }
        if (commonHeader.dl >= 2) {
            dstHost2 = readInt(data, offset);
            offset += 4;
        }
        if (commonHeader.dl >= 3) {
            dstHost3 = readInt(data, offset);
            offset += 4;
        }

        srcHost0 = readInt(data, offset);
        offset += 4;
        if (commonHeader.sl >= 1) {
            srcHost1 = readInt(data, offset);
            offset += 4;
        }
        if (commonHeader.sl >= 2) {
            srcHost2 = readInt(data, offset);
            offset += 4;
        }
        if (commonHeader.sl >= 3) {
            srcHost3 = readInt(data, offset);
            offset += 4;
        }

        len = offset - headerOffset;
        return offset;
    }

    public void reverse() {
        long dummy = srcIsdAs;
        srcIsdAs = dstIsdAs;
        dstIsdAs = dummy;

        int d;
        d = srcHost0;
        srcHost0 = dstHost0;
        dstHost0 = d;

        d = srcHost1;
        srcHost1 = dstHost1;
        dstHost1 = d;

        d = srcHost2;
        srcHost2 = dstHost2;
        dstHost2 = d;

        d = srcHost3;
        srcHost3 = dstHost3;
        dstHost3 = d;
    }

    public static int write(byte[] data, int offsetStart, CommonHeader commonHeader, AddressHeader inputHeader) {
        int offset = offsetStart;
//        long l0 = 0;
//        long l1 = 0;
//
//        l0 = writeLong(l0, 0, 16, inputHeader.srcISD);
//        l0 = writeLong(l0, 16, 48, inputHeader.srcAS);
//        l1 = writeLong(l1, 0, 16, inputHeader.dstISD);
//        l1 = writeLong(l1, 16, 48, inputHeader.dstAS);
        offset = writeLong(data, offset, inputHeader.dstIsdAs);
        offset = writeLong(data, offset, inputHeader.srcIsdAs);

        // HostAddr
        offset = writeInt(data, offset, inputHeader.dstHost0);
        if (commonHeader.dl >= 1) {
            offset = writeInt(data, offset, inputHeader.dstHost1);
        }
        if (commonHeader.dl >= 2) {
            offset = writeInt(data, offset, inputHeader.dstHost2);
        }
        if (commonHeader.dl >= 3) {
            offset = writeInt(data, offset, inputHeader.dstHost3);
        }

        offset = writeInt(data, offset, inputHeader.srcHost0);
        if (commonHeader.sl >= 1) {
            offset = writeInt(data, offset, inputHeader.srcHost1);
        }
        if (commonHeader.sl >= 2) {
            offset = writeInt(data, offset, inputHeader.srcHost2);
        }
        if (commonHeader.sl >= 3) {
            offset = writeInt(data, offset, inputHeader.srcHost3);
        }

        return offset;
    }

    public InetAddress getSrcHostAddress(byte[] data) {
        // TODO this is awkward, we should not pass in data[] here. (Or we should do it everywhere)
        byte[] bytes = new byte[(commonHeader.sl + 1) * 4];
        int offset = 16 + (commonHeader.dl + 1) * 4;
        System.arraycopy(data, commonHeader.length() + offset, bytes, 0, bytes.length);
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // This really should not happen
            throw new RuntimeException(e);
        }
    }

    public InetAddress getDstHostAddress() throws IOException {
        byte[] bytes = new byte[(commonHeader.dl + 1) * 4];
        if (commonHeader.dl == 0 && (commonHeader.dt == 0 || commonHeader.dt == 1)) {
            writeInt(bytes, 0, dstHost0);
        } else if (commonHeader.dl == 3 && commonHeader.dt == 0) {
            writeInt(bytes, 0, dstHost0);
            writeInt(bytes, 4, dstHost1);
            writeInt(bytes, 8, dstHost2);
            writeInt(bytes, 12, dstHost3);
        } else {
            throw new UnsupportedOperationException("Dst address not supported: DT/DL=" +
                    commonHeader.dt + "/" + commonHeader.dl);
        }
        return InetAddress.getByAddress(bytes);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Address Header: ");
        //            sb.append("  DstISD=" + dstISD);
        //            sb.append("  DstAS =" + dstAS);
        //            //sb.append("\n");
        //            sb.append("  SrcISD=" + srcISD);
        //            sb.append("  SrcAS =" + srcAS);
        //            System.out.println(sb);
        sb.append("  dstIsdAs=").append(Util.toStringIA(dstIsdAs));
        sb.append("  srcIsdAs=").append(Util.toStringIA(srcIsdAs));
        sb.append("  dstHost=").append(commonHeader.dt).append("/");
        if (commonHeader.dl == 0) {
            sb.append(Util.toStringIPv4(dstHost0)); // TODO dt 0=IPv$ or 1=Service
        } else if (commonHeader.dl == 3) {
            sb.append(Util.toStringIPv6(commonHeader.dl + 1, dstHost0, dstHost1, dstHost2, dstHost3));
        } else {
            sb.append("Format not recognized: ").append(Util.toStringIPv6(commonHeader.dl + 1, dstHost0, dstHost1, dstHost2, dstHost3));
        }
//            switch (commonHeader.dl) {
//                case 0: sb.append(Util.toStringIPv4(dstHost0)); break;
//                case 1: sb.append(Util.toStringIPv4(dstHost0)).append(Util.toStringIPv4(dstHost1)); break;
//                case 2: sb.append(Util.toStringIPv4(dstHost0)).append(Util.toStringIPv4(dstHost1)).append(Util.toStringIPv4(dstHost2)); break;
//                case 3: sb.append(Util.toStringIPv4(dstHost0)).append(Util.toStringIPv4(dstHost1)).append(Util.toStringIPv4(dstHost2)).append(Util.toStringIPv4(dstHost3)); break;
//                default:
//                    throw new IllegalArgumentException("DL=" + commonHeader.dl);
//            }
        sb.append("  srcHost=").append(commonHeader.st).append("/");
        if (commonHeader.sl == 0) {
            sb.append(Util.toStringIPv4(srcHost0)); // TODO dt 0=IPv$ or 1=Service
        } else if (commonHeader.sl == 3) {
            sb.append(Util.toStringIPv6(commonHeader.sl + 1, srcHost0, srcHost1, srcHost2, srcHost3));
        } else {
            sb.append("Format not recognized: ").append(Util.toStringIPv6(commonHeader.sl + 1, srcHost0, srcHost1, srcHost2, srcHost3));
        }
//            switch (commonHeader.sl) {
//                case 0: sb.append(Util.toStringIPv4(srcHost0)); break;
//                case 1: sb.append(Util.toStringIPv4(srcHost0)).append(Util.toStringIPv4(srcHost1)); break;
//                case 2: sb.append(Util.toStringIPv4(srcHost0)).append(Util.toStringIPv4(srcHost1)).append(Util.toStringIPv4(srcHost2)); break;
//                case 3: sb.append(Util.toStringIPv4(srcHost0)).append(Util.toStringIPv4(srcHost1)).append(Util.toStringIPv4(srcHost2)).append(Util.toStringIPv4(srcHost3)); break;
//                default:
//                    throw new IllegalArgumentException("SL=" + commonHeader.sl);
//            }
        return sb.toString();
    }

    public int length() {
        return len;
    }

    public void setSrcIA(long srcIsdAs) {
        this.srcIsdAs = srcIsdAs;
    }

    public void setDstIA(long dstIsdAs) {
        this.dstIsdAs = dstIsdAs;
    }

    public void setDstHostAddress(InetAddress address) {
        if (address instanceof Inet4Address) {
            commonHeader.dt = 0;
            commonHeader.dl = 0;
            byte[] bytes = address.getAddress();
            dstHost0 = readInt(bytes, 0);
            dstHost1 = 0;
            dstHost2 = 0;
            dstHost3 = 0;
        } else if (address instanceof Inet6Address) {
            commonHeader.dt = 0;
            commonHeader.dl = 3;
            byte[] bytes = address.getAddress();
            dstHost0 = readInt(bytes, 0);
            dstHost1 = readInt(bytes, 4);
            dstHost2 = readInt(bytes, 8);
            dstHost3 = readInt(bytes, 12);
        } else {
            throw new UnsupportedOperationException("Dst address class not supported: " + address.getClass().getName());
        }
    }


    public void setSrcHostAddress(InetAddress address) {
        if (address instanceof Inet4Address) {
            commonHeader.st = 0;
            commonHeader.sl = 0;
            byte[] bytes = address.getAddress();
            srcHost0 = readInt(bytes, 0);
            srcHost1 = 0;
            srcHost2 = 0;
            srcHost3 = 0;
        } else if (address instanceof Inet6Address) {
            commonHeader.st = 0;
            commonHeader.sl = 3;
            byte[] bytes = address.getAddress();
            srcHost0 = readInt(bytes, 0);
            srcHost1 = readInt(bytes, 4);
            srcHost2 = readInt(bytes, 8);
            srcHost3 = readInt(bytes, 12);
        } else {
            throw new UnsupportedOperationException("Dst address class not supported: " + address.getClass().getName());
        }
    }
}
