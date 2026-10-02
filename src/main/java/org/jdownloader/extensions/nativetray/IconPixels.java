// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.ArrayList;

public final class IconPixels {
    private IconPixels() { }
    public static byte[] argb(BufferedImage image) {
        byte[] bytes=new byte[image.getWidth()*image.getHeight()*4];
        int p=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int v=image.getRGB(x,y); bytes[p++]=(byte)(v>>>24);bytes[p++]=(byte)(v>>>16);bytes[p++]=(byte)(v>>>8);bytes[p++]=(byte)v;
        }
        return bytes;
    }
    public static List<Protocols.Pixmap> sizes(BufferedImage original, boolean grey, boolean clipboardOff) {
        List<Protocols.Pixmap> result=new ArrayList<>();
        for(int size: new int[]{16,22,24,32,48,64}) {
            BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(original,0,0,size,size,null);
            if(clipboardOff) { g.setColor(new java.awt.Color(205,55,55));g.fillOval(size/2,size/2,size/2,size/2);g.setColor(java.awt.Color.WHITE);g.fillRect(size*5/8,size*3/4,Math.max(2,size/4),Math.max(1,size/12)); }
            g.dispose();
            if(grey)for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
                int v=image.getRGB(x,y); int l=(((v>>16)&255)*299+((v>>8)&255)*587+(v&255)*114)/1000;
                image.setRGB(x,y,(v&0xff000000)|(l<<16)|(l<<8)|l);
            }
            result.add(new Protocols.Pixmap(size,size,argb(image)));
        }
        return List.copyOf(result);
    }
}
