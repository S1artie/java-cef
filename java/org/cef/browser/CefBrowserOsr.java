// Copyright (c) 2014 The Chromium Embedded Framework Authors. All rights
// reserved. Use of this source code is governed by a BSD-style license that
// can be found in the LICENSE file.

package org.cef.browser;

import com.jogamp.nativewindow.NativeSurface;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;

import org.cef.CefClient;
import org.cef.OS;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DropTarget;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

/**
 * This class represents an off-screen rendered browser. 
 * The visibility of this class is "package". To create a new
 * CefBrowser instance, please use CefBrowserFactory.
 */
class CefBrowserOsr extends CefBrowser_N implements CefRenderHandler {
	private CefRenderer renderer_;
	private GLCanvas canvas_;
	private long window_handle_ = 0;
	private Rectangle browser_rect_ = new Rectangle(0, 0, 1, 1); // Work around CEF issue #1437.
	private Point screenPoint_ = new Point(0, 0);
	private boolean isTransparent_;
	private double zoom_level_ = 0.0;

	CefBrowserOsr(CefClient client, String url, boolean transparent, CefRequestContext context) {
		this(client, url, transparent, context, null, null);
	}

	private CefBrowserOsr(CefClient client, String url, boolean transparent,
	    CefRequestContext context, CefBrowserOsr parent, Point inspectAt) {
		super(client, url, context, parent, inspectAt);
		isTransparent_ = transparent;
		renderer_ = new CefRenderer(transparent);
		createGLCanvas();
	}

	@Override
	public void createImmediately() {
		// Create the browser immediately.
		createBrowserIfRequired(false);
	}

	@Override
	public Component getUIComponent() {
		return canvas_;
	}

	@Override
	public CefRenderHandler getRenderHandler() {
		return this;
	}

	@Override
	protected CefBrowser_N createDevToolsBrowser(CefClient client, String url,
	    CefRequestContext context, CefBrowser_N parent, Point inspectAt) {
		return new CefBrowserOsr(
		        client, url, isTransparent_, context, (CefBrowserOsr) this, inspectAt);
	}

	private synchronized long getWindowHandle() {
		if (window_handle_ == 0) {
			NativeSurface surface = canvas_.getNativeSurface();
			if (surface != null) {
				surface.lockSurface();
				window_handle_ = getWindowHandle(surface.getSurfaceHandle());
				surface.unlockSurface();
				assert (window_handle_ != 0);
			}
		}
		return window_handle_;
	}

	@SuppressWarnings("serial")
	private void createGLCanvas() {
		GLProfile glprofile = GLProfile.getMaxFixedFunc(true);
		GLCapabilities glcapabilities = new GLCapabilities(glprofile);
		canvas_ = new GLCanvas(glcapabilities) {
			@Override
			public void paint(Graphics g) {
				createBrowserIfRequired(true);
				super.paint(g);
			}
        };

        canvas_.addGLEventListener(new GLEventListener() {
            @Override
            public void reshape(
                    GLAutoDrawable glautodrawable, int x, int y, int width, int height) {
                if (!OS.isMacintosh()) {
                    // HiDPI display scale correction support code
                    // For some reason this does not seem to be necessary on MacOS, so we don't do it. But on Windows, the
                    // browser content would be too small and in the lower left corner of the canvas only if we didn't
                    // scale the browser_rect_ accordingly.
                    browser_rect_.setBounds(x, y, (int) (width * getHiDPIScalingFactor()), (int) (height * getHiDPIScalingFactor()));
                } else {
                    browser_rect_.setBounds(x, y, width, height);
                }
                screenPoint_ = canvas_.getLocationOnScreen();
                wasResized(width, height);
			}

			@Override
			public void init(GLAutoDrawable glautodrawable) {
				renderer_.initialize(glautodrawable.getGL().getGL2());
			}

			@Override
			public void dispose(GLAutoDrawable glautodrawable) {
				renderer_.cleanup(glautodrawable.getGL().getGL2());
			}

			@Override
			public void display(GLAutoDrawable glautodrawable) {
				renderer_.render(glautodrawable.getGL().getGL2());
			}
		});

		canvas_.addMouseListener(new MouseListener() {
			@Override
			public void mousePressed(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}

			@Override
			public void mouseExited(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}
		});

		canvas_.addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseMoved(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				sendMouseEvent(rescaleEvent(e));
			}
		});

		canvas_.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				sendMouseWheelEvent(rescaleEvent(e));
			}
		});

		canvas_.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
				sendKeyEvent(e);
			}

			@Override
			public void keyPressed(KeyEvent e) {
				sendKeyEvent(e);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				sendKeyEvent(e);
			}
		});

		canvas_.setFocusable(true);
		canvas_.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				setFocus(false);
			}

			@Override
			public void focusGained(FocusEvent e) {
				// Dismiss any Java menus that are currently displayed.
				MenuSelectionManager.defaultManager().clearSelectedPath();
				setFocus(true);
			}
		});

		// Connect the Canvas with a drag and drop listener.
		new DropTarget(canvas_, new CefDropTargetListenerOsr(this));
	}

	@Override
	public Rectangle getViewRect(CefBrowser browser) {
		return browser_rect_;
	}

	@Override
	public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
		Point screenPoint = new Point(screenPoint_);
		screenPoint.translate(viewPoint.x, viewPoint.y);
		return screenPoint;
	}

	@Override
	public void onPopupShow(CefBrowser browser, boolean show) {
		if (!show) {
			renderer_.clearPopupRects();
			invalidate();
		}
	}

	@Override
	public void onPopupSize(CefBrowser browser, Rectangle size) {
		renderer_.onPopupSize(size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects,
            ByteBuffer buffer, int width, int height) {
        if(canvas_ == null || canvas_.getContext() == null) {
            return;
        }

        canvas_.getContext().makeCurrent();
        renderer_.onPaint(canvas_.getGL().getGL2(), popup, dirtyRects, buffer, width, height);
        canvas_.getContext().release();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                canvas_.display();
            }
		});
	}

    @Override
    public void onCursorChange(CefBrowser browser, final int cursorType) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                canvas_.setCursor(new Cursor(cursorType));
            }
		});
	}

	@Override
	public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
		// TODO(JCEF) Prepared for DnD support using OSR mode.
		return false;
	}

	@Override
	public void updateDragCursor(CefBrowser browser, int operation) {
		// TODO(JCEF) Prepared for DnD support using OSR mode.
	}

	private void createBrowserIfRequired(boolean hasParent) {
		long windowHandle = 0;
		if (hasParent) {
			windowHandle = getWindowHandle();
		}

        if (getNativeRef("CefBrowser") == 0) {
            if (getParentBrowser() != null) {
                createDevTools(getParentBrowser(), getClient(), windowHandle, true, isTransparent_,
                        null, getInspectAt());
            } else {
                createBrowser(getClient(), windowHandle, getUrl(), true, isTransparent_, null,
                        getRequestContext());
            }
        } else {
            // OSR windows cannot be reparented after creation.
            setFocus(true);
        }
    }
    
  // ----------------------------------------------------------------------------------------------------------------
  // HiDPI display scale correction support code
  //
  // This code depends on the application detecting the necessary HiDPI scaling correction factor and setting it via
  // setHiDPIScalingFactor.
  // ----------------------------------------------------------------------------------------------------------------

  @Override
  public void setHiDPIScalingFactor(double aFactor) {
    super.setHiDPIScalingFactor(aFactor);
    
    // Re-set the current zoom level, which allows the new DPI scaling factor to influence the final zoom level
    setZoomLevel(zoom_level_);
		
    // On MacOS, the above apparently is sufficient. On Windows (and probably on Linux as well) we must also cause a rescale
    // of the existing content here (and recalculate the browser_rect_). Both is done via a resizing to the already-present size.
    if (!OS.isMacintosh() && canvas_ != null) {
        // This fakes a resizing operation of the GL canvas to the already-used size, which in turn causes a rescale of the content
        canvas_.reshape(browser_rect_.x, browser_rect_.y, canvas_.getWidth(), canvas_.getHeight());
    }
  }

  @Override
  public void setZoomLevel(double aZoomLevel) {
  	zoom_level_ = aZoomLevel;
    double dpiFactor = getHiDPIScalingFactor();
  	
    if (dpiFactor == 1.0) {
  	    super.setZoomLevel(aZoomLevel);
    } else {
    	  // Zoom level is not in "factor" but a weird power-of-1.2 thing. We have to transform stuff here.
    	  double zoomFactor = Math.pow(1.2, aZoomLevel);
        double actualZoomFactor = zoomFactor * dpiFactor;
        double actualZoomLevel = Math.log(actualZoomFactor) / Math.log(1.2);

        super.setZoomLevel(actualZoomLevel);
    }
  }
  
  @Override
  public double getZoomLevel() {
  	  // Note that this method does get the ACTUAL effective zoom level that the browser tells us.
  	  // That one must not strictly be equal to the last set level (which is stored in zoom_level_).
      double dpiFactor = getHiDPIScalingFactor();
  	
	    if (dpiFactor == 1.0) {
	  	    return super.getZoomLevel();
	    } else {
	        double actualZoomLevel = super.getZoomLevel();
	        double actualZoomFactor = Math.pow(1.2, actualZoomLevel);
	        double zoomFactor = actualZoomFactor / dpiFactor;
	        double zoomLevel = Math.log(zoomFactor) / Math.log(1.2);
	        
	        return zoomLevel;
	    }
  }

  @SuppressWarnings("unchecked")
  protected <T extends AWTEvent> T rescaleEvent(T anEvent) {
    if (getHiDPIScalingFactor() == 1.0) {
        return anEvent;
    }

    T result = anEvent;
    if (anEvent instanceof MouseWheelEvent) {
        result = (T) new MouseWheelEvent((Component) anEvent.getSource(),
            anEvent.getID(),
           ((MouseWheelEvent) anEvent).getWhen(),
           ((MouseWheelEvent) anEvent).getModifiers(),
           (int) (((MouseWheelEvent) anEvent).getX() * getHiDPIScalingFactor()),
           (int) (((MouseWheelEvent) anEvent).getY() * getHiDPIScalingFactor()),
           ((MouseWheelEvent) anEvent).getXOnScreen(),
           ((MouseWheelEvent) anEvent).getYOnScreen(),
           ((MouseWheelEvent) anEvent).getClickCount(),
           ((MouseWheelEvent) anEvent).isPopupTrigger(),
           ((MouseWheelEvent) anEvent).getScrollType(),
           ((MouseWheelEvent) anEvent).getScrollAmount(),
           		((MouseWheelEvent) anEvent).getWheelRotation(),
           ((MouseWheelEvent) anEvent).getPreciseWheelRotation());
    } else if (anEvent instanceof MouseEvent) {
        result = (T) new MouseEvent((Component) anEvent.getSource(),
           anEvent.getID(),
           ((MouseEvent) anEvent).getWhen(),
           ((MouseEvent) anEvent).getModifiers(),
           (int) (((MouseEvent) anEvent).getX() * getHiDPIScalingFactor()),
           (int) (((MouseEvent) anEvent).getY() * getHiDPIScalingFactor()),
           ((MouseEvent) anEvent).getXOnScreen(),
           ((MouseEvent) anEvent).getYOnScreen(),
           ((MouseEvent) anEvent).getClickCount(),
           ((MouseEvent) anEvent).isPopupTrigger(),
           ((MouseEvent) anEvent).getButton());
    }

    return result;
  }

    // ----------------------------------------------------------------------------------------------------------------
    // Image capture functionality
    // This was originally placed in the bundle wrapping JCEF, but then moved to JCEF itself because that allows us
	// to encapsulate JCEF and the JOGL libs into a bundle that only exports JCEF (and no JOGL classes).
	// ----------------------------------------------------------------------------------------------------------------

	@Override
	public BufferedImage createScreenshot() {
		int tempWidth = canvas_.getWidth();
		int tempHeight = canvas_.getHeight();
		BufferedImage tempScreenshot = new BufferedImage((int) (tempWidth * getHiDPIScalingFactor()),
				(int) (tempHeight * getHiDPIScalingFactor()),
				BufferedImage.TYPE_INT_RGB);
		Graphics tempGraphics = tempScreenshot.getGraphics();

		// In order to grab a screenshot of the browser window, we need to get the OpenGL internals from the GLCanvas
		// that displays the browser. Technically, this display component works by having Chromium render updated
		// parts into a 2D texture that is the same size as the window. On systems with 3D acceleration, this can be
		// done by directly copying stuff in the graphics card memory, which makes it fast. This texture is then
		// rendered into the canvas by rendering a simple textured quad via OpenGL. Our screen-grabbing mechanism works
		// by getting the texture ID from the renderers' internals and adding itself into the OpenGL rendering process:
		// the next time that rendering occurs, we just grab the textures' pixel data from the graphics memory and
		// store it as a BufferedImage, hence we get our perfect screenshot. To ensure rendering happens soon, we just
		// request an immediate redraw of the canvas' contents, which then causes rendering.
		GL2 tempGL = canvas_.getGL().getGL2();
		int tempTextureId = renderer_.getTextureID();

		// This mirrors the two ways in which CefRenderer may render images internally - either via a texture that is
		// updated incrementally and rendered by graphics hardware, in which case we capture the data directly from
		// the texture, or by directly writing pixels to the framebuffer, in which case we directly read those pixels
		// back. The latter is the way chosen if there is no graphics rendering hardware detected.
		// Both is done in the GLEventListener below, because we need a valid OpenGL context for both actions.
		boolean tempUseReadPixels = (tempTextureId == 0);
		final Object tempSyncObject = new Object();

		canvas_.addGLEventListener(new GLEventListener() {

			@Override
			public void reshape(GLAutoDrawable aDrawable, int aArg1, int aArg2, int aArg3, int aArg4) {
				// ignore
			}

			@Override
			public void init(GLAutoDrawable aDrawable) {
				// ignore
			}

			@Override
			public void dispose(GLAutoDrawable aDrawable) {
				// ignore
			}

			@Override
			public void display(GLAutoDrawable aDrawable) {
				ByteBuffer tempBuffer = GLBuffers.newDirectByteBuffer(
						(int) (tempWidth * getHiDPIScalingFactor()) * (int) (tempHeight * getHiDPIScalingFactor()) * 4);

				if (tempUseReadPixels) {
					// If pixels are copied directly to the framebuffer, we also directly read them back. In this case
					// we have to swap the resulting image on the Y axis, as OpenGL framebuffers are top-to-bottom.
					// This flipping is done below, when drawing into the BufferedImage.
					tempGL.glReadPixels(0, 0, tempWidth, tempHeight, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, tempBuffer);
				} else {
					tempGL.glEnable(GL.GL_TEXTURE_2D);
					tempGL.glBindTexture(GL.GL_TEXTURE_2D, tempTextureId);
					tempGL.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, tempBuffer);
					tempGL.glDisable(GL.GL_TEXTURE_2D);
				}

				for (int tempY = 0; tempY < (int) (tempHeight * getHiDPIScalingFactor()); tempY++) {
					for (int tempX = 0; tempX < (int) (tempWidth * getHiDPIScalingFactor()); tempX++) {
						Color tempPixelColor = new Color((tempBuffer.get() & 0xff),
								(tempBuffer.get() & 0xff),
								(tempBuffer.get() & 0xff));
						tempBuffer.get(); // throw away the alpha part
						tempGraphics.setColor(tempPixelColor);
						tempGraphics.drawRect(tempX,
								tempUseReadPixels ? ((int) (tempHeight * getHiDPIScalingFactor()) - tempY - 1) : tempY,
								1,
								1);
					}
				}

				synchronized (tempSyncObject) {
					tempSyncObject.notify();
					canvas_.removeGLEventListener(this);
				}
			}
		});

		// This repaint triggers a call to the listeners' display method above.
		canvas_.repaint();
		synchronized (tempSyncObject) {
			try {
				// Then we just have to wait until we're signalled by the listener being called
				tempSyncObject.wait(2000);
			} catch (InterruptedException exc) {
				// ignored
			}
		}

		// At this point in time, our screenshot image contains an actual shot of the browsers' internal texture.

		if (getHiDPIScalingFactor() > 1.0) {
			// HiDPI images should be resized down to "normal" levels
			BufferedImage tempResized = new BufferedImage((int) (tempScreenshot.getWidth() / getHiDPIScalingFactor()),
					(int) (tempScreenshot.getHeight() / getHiDPIScalingFactor()),
					BufferedImage.TYPE_INT_ARGB);
			AffineTransform tempTransform = new AffineTransform();
			tempTransform.scale(1.0 / getHiDPIScalingFactor(), 1.0 / getHiDPIScalingFactor());
			AffineTransformOp tempScaleOperation =
					new AffineTransformOp(tempTransform, AffineTransformOp.TYPE_BILINEAR);
			tempResized = tempScaleOperation.filter(tempScreenshot, tempResized);
			tempScreenshot = tempResized;
		}

		return tempScreenshot;
	}
}
