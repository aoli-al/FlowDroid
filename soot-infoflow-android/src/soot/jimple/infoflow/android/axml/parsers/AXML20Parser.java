package soot.jimple.infoflow.android.axml.parsers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlVisitor;
import pxb.android.axml.NodeVisitor;
import pxb.android.axml.ValueWrapper;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootResolver.SootClassNotFoundException;
import soot.jimple.infoflow.android.axml.AXmlAttribute;
import soot.jimple.infoflow.android.axml.AXmlColorValue;
import soot.jimple.infoflow.android.axml.AXmlComplexValue;
import soot.jimple.infoflow.android.axml.AXmlNamespace;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.tagkit.IntegerConstantValueTag;
import soot.tagkit.Tag;

/**
 * Class for parsing Android binary XML files using the AXMLPrinter2 library
 * 
 * @author Steven Arzt
 */
public class AXML20Parser extends AbstractBinaryXMLFileParser {

	private final Map<Integer, String> idToNameMap = new HashMap<Integer, String>();

	private class MyNodeVisitor extends AxmlVisitor {

		public final AXmlNode node;

		public MyNodeVisitor() {
			this.node = new AXmlNode("dummy", "", null);
		}

		public MyNodeVisitor(AXmlNode node) {
			this.node = node;
		}

		@Override
		public void attr(String ns, String name, int resourceId, int type, Object obj) {
			if (this.node == null)
				throw new RuntimeException("NULL nodes cannot have attributes");

			String tname = name;

			// If we have no node name, we use the resourceId to look up the
			// attribute in the android.R.attr class.
			if (tname == null || tname.isEmpty()) {
				tname = idToNameMap.get(resourceId);
				if (tname == null) {
					try {
						SootClass rClass = Scene.v().forceResolve("android.R$attr", SootClass.BODIES);
						if (rClass == null) {
							// Without a name, we cannot really carry on
							return;
						}

						outer: for (SootField sf : rClass.getFields())
							for (Tag t : sf.getTags())
								if (t instanceof IntegerConstantValueTag) {
									IntegerConstantValueTag cvt = (IntegerConstantValueTag) t;
									if (cvt.getIntValue() == resourceId) {
										tname = sf.getName();
										idToNameMap.put(resourceId, tname);
										// fake the Android namespace
										ns = "http://schemas.android.com/apk/res/android";
										break outer;
									}
									break;
								}
					} catch (SootClassNotFoundException ex) {
						// Without a name, we cannot really carry on
						return;
					}
					if (tname == null)
						tname = String.valueOf(resourceId);
				}
			} else
				tname = name.trim();

			// Read out the field data
			if (type == AXmlConstants.TYPE_REFERENCE || type == AXmlConstants.TYPE_INT_HEX
					|| type == AXmlConstants.TYPE_INT_DEC) {
				if (obj instanceof Integer)
					this.node.addAttribute(
							new AXmlAttribute<Integer>(tname, resourceId, type, (Integer) obj, ns, false));
				else if (obj instanceof ValueWrapper) {
					ValueWrapper wrapper = (ValueWrapper) obj;

					if (wrapper.raw != null)
						this.node.addAttribute(
								new AXmlAttribute<String>(tname, resourceId, type, wrapper.raw, ns, false));
					else if (wrapper.type == ValueWrapper.ID) {
						this.node.addAttribute(
								new AXmlAttribute<Integer>(tname, resourceId, type, wrapper.ref, ns, false));
					}
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_STRING) {
				if (obj instanceof String)
					this.node.addAttribute(new AXmlAttribute<String>(tname, resourceId, type, (String) obj, ns, false));
				else if (obj instanceof ValueWrapper) {
					ValueWrapper wrapper = (ValueWrapper) obj;
					this.node.addAttribute(new AXmlAttribute<String>(tname, resourceId, type, wrapper.raw, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_INT_BOOLEAN) {
				if (obj instanceof Boolean)
					this.node.addAttribute(
							new AXmlAttribute<Boolean>(tname, resourceId, type, (Boolean) obj, ns, false));
				else if (obj instanceof ValueWrapper) {
					ValueWrapper wrapper = (ValueWrapper) obj;
					this.node.addAttribute(new AXmlAttribute<Boolean>(tname, resourceId, type,
							Boolean.valueOf(wrapper.raw), ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_FLOAT) {
				if (obj instanceof Integer) {
					float floatVal = Float.intBitsToFloat((Integer) obj);
					this.node.addAttribute(new AXmlAttribute<Float>(tname, resourceId, type, floatVal, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_DIMENSION) {
				if (obj instanceof Integer) {
					int x = (Integer) obj;
					AXmlComplexValue complexValue = AXmlComplexValue.parseComplexValue(x);
					if (complexValue.getInt() != x)
						throw new RuntimeException("Miscalculated: Original complex values is " + x
								+ "; reinterpreted is " + complexValue.getInt());
					this.node.addAttribute(
							new AXmlAttribute<AXmlComplexValue>(tname, resourceId, type, complexValue, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_INT_COLOR_ARGB8) {
				if (obj instanceof Integer) {
					int color = (Integer) obj;
					int bb = color & 0x000000FF;
					int gg = (color & 0x0000FF00) >> 8;
					int rr = (color & 0x00FF0000) >> 16;
					int aa = (color & 0xFF000000) >> 24;
					AXmlColorValue colorVal = new AXmlColorValue(aa, rr, gg, bb);
					this.node.addAttribute(
							new AXmlAttribute<AXmlColorValue>(tname, resourceId, type, colorVal, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_INT_COLOR_ARGB4) {
				if (obj instanceof Integer) {
					int color = (Integer) obj;
					int b = color & 0x000F << 4;
					int g = (color & 0x00F0);
					int r = (color & 0x0F00) >> 4;
					int a = (color & 0xF000) >> 8;
					AXmlColorValue colorVal = new AXmlColorValue(a, r, g, b);
					this.node.addAttribute(
							new AXmlAttribute<AXmlColorValue>(tname, resourceId, type, colorVal, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_INT_COLOR_RGB8) {
				if (obj instanceof Integer) {
					int color = (Integer) obj;
					int bb = color & 0x000000FF;
					int gg = (color & 0x0000FF00) >> 8;
					int rr = (color & 0x00FF0000) >> 16;
					AXmlColorValue colorVal = new AXmlColorValue(rr, gg, bb);
					this.node.addAttribute(
							new AXmlAttribute<AXmlColorValue>(tname, resourceId, type, colorVal, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			} else if (type == AXmlConstants.TYPE_INT_COLOR_RGB4) {
				if (obj instanceof Integer) {
					int color = (Integer) obj;
					int b = color & 0x000F << 4;
					int g = (color & 0x00F0);
					int r = (color & 0x0F00) >> 4;
					AXmlColorValue colorVal = new AXmlColorValue(r, g, b);
					this.node.addAttribute(
							new AXmlAttribute<AXmlColorValue>(tname, resourceId, type, colorVal, ns, false));
				} else
					throw new RuntimeException("Unsupported value type");
			}

			super.attr(ns, name, resourceId, type, obj);
		}

		@Override
		public NodeVisitor child(String ns, String name) {
			AXmlNode childNode = new AXmlNode(name == null ? null : name.trim(), ns == null ? null : ns.trim(), node);
			if (name != null)
				addPointer(name, childNode);
			return new MyNodeVisitor(childNode);
		}

		@Override
		public void end() {
			document.setRootNode(node);
		}

		@Override
		public void ns(String prefix, String uri, int line) {
			document.addNamespace(new AXmlNamespace(prefix, uri, line));
		}

		@Override
		public void text(int lineNumber, String value) {
			node.setText(value);
			super.text(lineNumber, value);
		}

	}

	@Override
	public void parseFile(byte[] buffer) throws IOException {
		AxmlReader rdr = new AxmlReader(buffer);
		rdr.accept(new MyNodeVisitor());
	}

}
