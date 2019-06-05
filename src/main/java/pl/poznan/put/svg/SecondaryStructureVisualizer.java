package pl.poznan.put.svg;

import fr.orsay.lri.varna.exceptions.ExceptionFileFormatOrSyntax;
import fr.orsay.lri.varna.exceptions.ExceptionNAViewAlgorithm;
import fr.orsay.lri.varna.exceptions.ExceptionUnmatchedClosingParentheses;
import fr.orsay.lri.varna.exceptions.ExceptionWritingForbidden;
import fr.orsay.lri.varna.models.VARNAConfig;
import fr.orsay.lri.varna.models.rna.ModelBaseStyle;
import fr.orsay.lri.varna.models.rna.ModeleBase;
import fr.orsay.lri.varna.models.rna.ModeleColorMap;
import fr.orsay.lri.varna.models.rna.RNA;
import org.apache.batik.util.SVGConstants;
import org.apache.commons.io.FileUtils;
import org.jcolorbrewer.ColorBrewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;
import pl.poznan.put.comparison.mapping.ComparisonMapper;
import pl.poznan.put.matching.FragmentMatch;
import pl.poznan.put.matching.ResidueComparison;
import pl.poznan.put.pdb.analysis.PdbCompactFragment;
import pl.poznan.put.structure.secondary.CanonicalStructureExtractor;
import pl.poznan.put.structure.secondary.formats.*;
import pl.poznan.put.structure.secondary.pseudoknots.elimination.MinGain;
import pl.poznan.put.torsion.MasterTorsionAngleType;
import pl.poznan.put.utility.ResourcesHelper;
import pl.poznan.put.utility.svg.SVGHelper;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.IntStream;

public final class SecondaryStructureVisualizer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryStructureVisualizer.class);
  private static final Converter CONVERTER = new LevelByLevelConverter(new MinGain(), 1);

  private SecondaryStructureVisualizer() {
    super();
  }

  public static SVGDocument visualize(
      final FragmentMatch fragmentMatch, final ComparisonMapper mapper) {
    try {
      final List<ResidueComparison> residueComparisons = fragmentMatch.getResidueComparisons();
      final List<MasterTorsionAngleType> angleTypes = fragmentMatch.getAngleTypes();
      final Double[] mapped = mapper.map(residueComparisons, angleTypes);

      final DotBracket dotBracket = SecondaryStructureVisualizer.getTargetDotBracket(fragmentMatch);
      return SecondaryStructureVisualizer.visualize(dotBracket, mapped);
    } catch (final InvalidStructureException e) {
      SecondaryStructureVisualizer.LOGGER.error(
          "Failed to extract canonical secondary structure", e);
      return SVGHelper.emptyDocument();
    }
  }

  private static DotBracket getTargetDotBracket(final FragmentMatch fragmentMatch)
      throws InvalidStructureException {
    final PdbCompactFragment target = fragmentMatch.getTargetFragment();
    final BpSeq bpSeq = CanonicalStructureExtractor.bpSeq(target);
    return SecondaryStructureVisualizer.CONVERTER.convert(bpSeq);
  }

  public static SVGDocument visualize(final DotBracketInterface dotBracket, final Double[] mapped) {
    File tempFile = null;

    try {
      tempFile = File.createTempFile("mcq4structures", ".svg");

      final ModeleColorMap modelColorMap = SecondaryStructureVisualizer.colorMap();
      final VARNAConfig config = new VARNAConfig();

      final RNA rna = new RNA();
      rna.setRNA(dotBracket.getSequence(), dotBracket.getStructure());

      final List<ModeleBase> listeBases = rna.get_listeBases();
      IntStream.range(0, listeBases.size())
          .forEach(
              i -> {
                final ModeleBase modeleBase = listeBases.get(i);
                final ModelBaseStyle modelBaseStyle = modeleBase.getStyleBase();
                modelBaseStyle.setBaseInnerColor(modelColorMap.getColorForValue(mapped[i]));
              });

      rna.drawRNANAView(config);
      rna.saveRNASVG(tempFile.getAbsolutePath(), config);

      final SVGDocument svgDocument = SVGHelper.fromFile(tempFile);
      final SVGSVGElement root = svgDocument.getRootElement();

      final Rectangle2D boundingBox = SVGHelper.calculateBoundingBox(svgDocument);
      root.setAttributeNS(
          null,
          SVGConstants.SVG_VIEW_BOX_ATTRIBUTE,
          String.format(
              "%s %s %s %s",
              boundingBox.getMinX(),
              boundingBox.getMinY(),
              boundingBox.getWidth(),
              boundingBox.getHeight()));
      root.setAttributeNS(
          null, SVGConstants.SVG_WIDTH_ATTRIBUTE, Double.toString(boundingBox.getWidth()));
      root.setAttributeNS(
          null, SVGConstants.SVG_HEIGHT_ATTRIBUTE, Double.toString(boundingBox.getHeight()));

      final URI uri = ResourcesHelper.loadResourceUri("mcq-legend.svg");
      final SVGDocument legend = SVGHelper.fromUri(uri);
      return SVGHelper.merge(svgDocument, legend);
    } catch (final ExceptionUnmatchedClosingParentheses
        | ExceptionFileFormatOrSyntax
        | ExceptionWritingForbidden
        | ExceptionNAViewAlgorithm
        | IOException
        | URISyntaxException e) {
      SecondaryStructureVisualizer.LOGGER.error(
          "Failed to visualize secondary structure:\n{}", dotBracket, e);
    } finally {
      FileUtils.deleteQuietly(tempFile);
    }

    return SVGHelper.emptyDocument();
  }

  private static ModeleColorMap colorMap() {
    final Color[] colorPalette = ColorBrewer.YlOrRd.getColorPalette(4);
    final ModeleColorMap colorMap = new ModeleColorMap();
    colorMap.addColor(0.0, colorPalette[0]);
    colorMap.addColor(1.0 / 3.0, colorPalette[1]);
    colorMap.addColor(2.0 / 3.0, colorPalette[2]);
    colorMap.addColor(1.0, colorPalette[3]);
    return colorMap;
  }
}
