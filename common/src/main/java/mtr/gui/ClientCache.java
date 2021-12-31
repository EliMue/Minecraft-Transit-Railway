package mtr.gui;

import mtr.MTR;
import mtr.data.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.text.AttributedString;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ClientCache extends DataCache {

	private Font font;
	private Font fontCjk;

	public final Map<BlockPos, List<Platform>> posToPlatforms = new HashMap<>();
	public final Map<BlockPos, List<Siding>> posToSidings = new HashMap<>();
	public final Map<Long, Map<Integer, ColorNameTuple>> stationIdToRoutes = new HashMap<>();

	private final Map<Long, Map<Long, Platform>> stationIdToPlatforms = new HashMap<>();
	private final Map<Long, Map<Long, Siding>> depotIdToSidings = new HashMap<>();
	private final Map<Long, List<PlatformRouteDetails>> platformIdToRoutes = new HashMap<>();

	private final List<Long> clearStationIdToPlatforms = new ArrayList<>();
	private final List<Long> clearDepotIdToSidings = new ArrayList<>();
	private final List<Long> clearPlatformIdToRoutes = new ArrayList<>();
	private final List<String> clearDynamicResources = new ArrayList<>();

	private final Map<String, ResourceLocation> dynamicResources = new HashMap<>();
	private boolean canGenerateResource = true;

	private static final float LINE_HEIGHT_MULTIPLIER = 1.25F;

	public ClientCache(Set<Station> stations, Set<Platform> platforms, Set<Siding> sidings, Set<Route> routes, Set<Depot> depots) {
		super(stations, platforms, sidings, routes, depots);
	}

	@Override
	protected void syncAdditional() {
		mapPosToSavedRails(posToPlatforms, platforms);
		mapPosToSavedRails(posToSidings, sidings);

		stationIdToRoutes.clear();
		routes.forEach(route -> route.platformIds.forEach(platformId -> {
			final Station station = platformIdToStation.get(platformId);
			if (station != null) {
				if (!stationIdToRoutes.containsKey(station.id)) {
					stationIdToRoutes.put(station.id, new HashMap<>());
				}
				stationIdToRoutes.get(station.id).put(route.color, new ColorNameTuple(route.color, route.name.split("\\|\\|")[0]));
			}
		}));

		stationIdToPlatforms.keySet().forEach(id -> {
			if (!clearStationIdToPlatforms.contains(id)) {
				clearStationIdToPlatforms.add(id);
			}
		});
		depotIdToSidings.keySet().forEach(id -> {
			if (!clearDepotIdToSidings.contains(id)) {
				clearDepotIdToSidings.add(id);
			}
		});
		platformIdToRoutes.keySet().forEach(id -> {
			if (!clearPlatformIdToRoutes.contains(id)) {
				clearPlatformIdToRoutes.add(id);
			}
		});
		dynamicResources.keySet().forEach(id -> {
			if (!clearDynamicResources.contains(id)) {
				clearDynamicResources.add(id);
			}
		});
	}

	public Map<Long, Platform> requestStationIdToPlatforms(long stationId) {
		if (!stationIdToPlatforms.containsKey(stationId)) {
			final Station station = stationIdMap.get(stationId);
			if (station != null) {
				stationIdToPlatforms.put(stationId, areaIdToSavedRails(station, platforms));
			} else {
				stationIdToPlatforms.put(stationId, new HashMap<>());
			}
		}
		return stationIdToPlatforms.get(stationId);
	}

	public Map<Long, Siding> requestDepotIdToSidings(long depotId) {
		if (!depotIdToSidings.containsKey(depotId)) {
			final Depot depot = depotIdMap.get(depotId);
			if (depot != null) {
				depotIdToSidings.put(depotId, areaIdToSavedRails(depot, sidings));
			} else {
				depotIdToSidings.put(depotId, new HashMap<>());
			}
		}
		return depotIdToSidings.get(depotId);
	}

	public List<PlatformRouteDetails> requestPlatformIdToRoutes(long platformId) {
		if (!platformIdToRoutes.containsKey(platformId)) {
			platformIdToRoutes.put(platformId, routes.stream().filter(route -> route.platformIds.contains(platformId)).map(route -> {
				final List<PlatformRouteDetails.StationDetails> stationDetails = route.platformIds.stream().map(platformId2 -> {
					final Station station = platformIdToStation.get(platformId2);
					if (station == null) {
						return new PlatformRouteDetails.StationDetails("", new ArrayList<>());
					} else {
						return new PlatformRouteDetails.StationDetails(station.name, stationIdToRoutes.get(station.id).values().stream().filter(colorNameTuple -> colorNameTuple.color != route.color).collect(Collectors.toList()));
					}
				}).collect(Collectors.toList());
				return new PlatformRouteDetails(route.name.split("\\|\\|")[0], route.color, route.circularState, route.platformIds.indexOf(platformId), stationDetails);
			}).collect(Collectors.toList()));
		}
		return platformIdToRoutes.get(platformId);
	}

	public ResourceLocation getColorStrip(long platformId) {
		return getResource(String.format("color_%s", platformId), "textures/block/transparent.png", () -> RouteMapGenerator.generateColorStrip(platformId));
	}

	public ResourceLocation getStationName(long platformId, float aspectRatio) {
		return getResource(String.format("name_%s_%s", platformId, aspectRatio), "textures/block/white.png", () -> RouteMapGenerator.generateStationName(platformId, aspectRatio));
	}

	public ResourceLocation getDirectionArrow(long platformId, boolean invert, boolean renderWhite, boolean hasLeft, boolean hasRight, boolean showToString, float aspectRatio) {
		return getResource(String.format("map_%s_%s_%s_%s_%s_%s_%s", platformId, invert, renderWhite, hasLeft, hasRight, showToString, aspectRatio), renderWhite ? "textures/block/white.png" : "textures/block/transparent.png", () -> RouteMapGenerator.generateDirectionArrow(platformId, invert, renderWhite, hasLeft, hasRight, showToString, aspectRatio));
	}

	public ResourceLocation getRouteMap(long platformId, boolean renderWhite, boolean vertical, boolean flip, float aspectRatio) {
		return getResource(String.format("map_%s_%s_%s,%s_%s", platformId, renderWhite, vertical, flip, aspectRatio), renderWhite ? "textures/block/white.png" : "textures/block/transparent.png", () -> RouteMapGenerator.generateRouteMap(platformId, renderWhite, vertical, flip, aspectRatio));
	}

	public byte[] getTextPixels(String text, int[] dimensions, int fontSizeCjk, int fontSize) {
		return getTextPixels(text, dimensions, Integer.MAX_VALUE, fontSizeCjk, fontSize, 0, null);
	}

	public byte[] getTextPixels(String text, int[] dimensions, int maxWidth, int fontSizeCjk, int fontSize, int padding, IGui.HorizontalAlignment horizontalAlignment) {
		final boolean oneRow = horizontalAlignment == null;
		final String[] textSplit = IGui.textOrUntitled(text).split("\\|");
		final AttributedString[] attributedStrings = new AttributedString[textSplit.length];
		final int[] textWidths = new int[textSplit.length];
		final int[] fontSizes = new int[textSplit.length];
		final FontRenderContext context = new FontRenderContext(new AffineTransform(), false, false);
		int width = 0;
		int height = 0;

		for (int index = 0; index < textSplit.length; index++) {
			final boolean isCjk = textSplit[index].codePoints().anyMatch(Character::isIdeographic);
			final Font mainFont = font.deriveFont(Font.PLAIN, isCjk ? fontSizeCjk : fontSize);
			final Font fallbackFont = isCjk ? fontCjk.deriveFont(Font.PLAIN, fontSizeCjk) : mainFont;

			attributedStrings[index] = new AttributedString(textSplit[index]);
			attributedStrings[index].addAttribute(TextAttribute.FONT, mainFont, 0, textSplit[index].length());
			fontSizes[index] = isCjk ? fontSizeCjk : fontSize;

			for (int characterIndex = 0; characterIndex < textSplit[index].length(); characterIndex++) {
				final boolean useFallback = !mainFont.canDisplay(textSplit[index].charAt(characterIndex));
				textWidths[index] += (useFallback ? fallbackFont : mainFont).getStringBounds(textSplit[index].substring(characterIndex, characterIndex + 1), context).getBounds().width;
				attributedStrings[index].addAttribute(TextAttribute.FONT, (useFallback ? fallbackFont : mainFont), characterIndex, characterIndex + 1);
			}

			if (oneRow) {
				if (index > 0) {
					width += padding;
				}
				width += textWidths[index];
				height = Math.max(height, (int) (fontSizes[index] * LINE_HEIGHT_MULTIPLIER));
			} else {
				width = Math.max(width, Math.min(maxWidth, textWidths[index]));
				height += fontSizes[index] * LINE_HEIGHT_MULTIPLIER;
			}
		}

		int textOffset = 0;
		final BufferedImage image = new BufferedImage(width + (oneRow ? 0 : padding * 2), height + (oneRow ? 0 : padding * 2), BufferedImage.TYPE_BYTE_GRAY);
		final Graphics2D graphics2D = image.createGraphics();
		graphics2D.setColor(Color.WHITE);
		graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		for (int index = 0; index < textSplit.length; index++) {
			if (oneRow) {
				graphics2D.drawString(attributedStrings[index].getIterator(), textOffset, height / LINE_HEIGHT_MULTIPLIER);
				textOffset += textWidths[index] + padding;
			} else {
				final int textWidth = Math.min(maxWidth, textWidths[index]);
				final AffineTransform stretch = new AffineTransform();
				final float scale = (float) textWidth / textWidths[index];
				stretch.concatenate(AffineTransform.getScaleInstance(scale, 1));
				graphics2D.setTransform(stretch);
				graphics2D.drawString(attributedStrings[index].getIterator(), horizontalAlignment.getOffset(0, textWidth - width) + padding / scale, textOffset + fontSizes[index] + padding);
				textOffset += fontSizes[index] * LINE_HEIGHT_MULTIPLIER;
			}
		}

		dimensions[0] = width + (oneRow ? 0 : padding * 2);
		dimensions[1] = height + (oneRow ? 0 : padding * 2);
		final byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
		graphics2D.dispose();
		image.flush();
		return pixels;
	}

	public void clearDataIfNeeded() {
		if (!clearStationIdToPlatforms.isEmpty()) {
			stationIdToPlatforms.remove(clearStationIdToPlatforms.remove(0));
		}
		if (!clearDepotIdToSidings.isEmpty()) {
			depotIdToSidings.remove(clearDepotIdToSidings.remove(0));
		}
		if (!clearPlatformIdToRoutes.isEmpty()) {
			platformIdToRoutes.remove(clearPlatformIdToRoutes.remove(0));
		}
		if (!clearDynamicResources.isEmpty()) {
			dynamicResources.remove(clearDynamicResources.remove(0));
		}
	}

	private ResourceLocation getResource(String key, String defaultResource, Supplier<DynamicTexture> supplier) {
		final Minecraft minecraftClient = Minecraft.getInstance();
		if (font == null || fontCjk == null) {
			final ResourceManager resourceManager = minecraftClient.getResourceManager();
			try {
				font = Font.createFont(Font.TRUETYPE_FONT, resourceManager.getResource(new ResourceLocation(MTR.MOD_ID, "font/noto-sans-semibold.ttf")).getInputStream());
				fontCjk = Font.createFont(Font.TRUETYPE_FONT, resourceManager.getResource(new ResourceLocation(MTR.MOD_ID, "font/noto-serif-cjk-tc-semibold.ttf")).getInputStream());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (dynamicResources.containsKey(key)) {
			return dynamicResources.get(key);
		} else {
			final ResourceLocation defaultLocation = new ResourceLocation(MTR.MOD_ID, defaultResource);
			if (canGenerateResource) {
				canGenerateResource = false;
				new Thread(() -> {
					final DynamicTexture dynamicTexture = supplier.get();
					minecraftClient.execute(() -> {
						dynamicResources.put(key, dynamicTexture == null ? defaultLocation : minecraftClient.getTextureManager().register(MTR.MOD_ID, dynamicTexture));
						canGenerateResource = true;
					});
				}).start();
			}

			return defaultLocation;
		}
	}

	private static <U extends AreaBase, V extends SavedRailBase> Map<Long, V> areaIdToSavedRails(U area, Set<V> savedRails) {
		final Map<Long, V> savedRailMap = new HashMap<>();
		savedRails.forEach(savedRail -> {
			final BlockPos pos = savedRail.getMidPos();
			if (area.inArea(pos.getX(), pos.getZ())) {
				savedRailMap.put(savedRail.id, savedRail);
			}
		});
		return savedRailMap;
	}

	private static <U extends SavedRailBase> void mapPosToSavedRails(Map<BlockPos, List<U>> posToSavedRails, Set<U> savedRails) {
		posToSavedRails.clear();
		savedRails.forEach(savedRail -> {
			final BlockPos pos = savedRail.getMidPos(true);
			if (!posToSavedRails.containsKey(pos)) {
				posToSavedRails.put(pos, new ArrayList<>());
			}
			posToSavedRails.get(pos).add(savedRail);
		});
	}

	public static class PlatformRouteDetails {

		public final String routeName;
		public final int routeColor;
		public final Route.CircularState circularState;
		public final int currentStationIndex;
		public final List<StationDetails> stationDetails;

		public PlatformRouteDetails(String routeName, int routeColor, Route.CircularState circularState, int currentStationIndex, List<StationDetails> stationDetails) {
			this.routeName = routeName;
			this.routeColor = routeColor;
			this.circularState = circularState;
			this.currentStationIndex = currentStationIndex;
			this.stationDetails = stationDetails;
		}

		public static class StationDetails {

			public final String stationName;
			public final List<ColorNameTuple> interchangeRoutes;

			public StationDetails(String stationName, List<ColorNameTuple> interchangeRoutes) {
				this.stationName = stationName;
				this.interchangeRoutes = interchangeRoutes;
			}
		}
	}

	public static class ColorNameTuple {

		public final int color;
		public final String name;

		public ColorNameTuple(int color, String name) {
			this.color = color;
			this.name = name;
		}
	}
}
