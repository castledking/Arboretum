package codes.castled.arboretum.core.template;

/**
 * One block placement inside a {@link StructureTemplate}: a template-local position and an index
 * into the template's palette.
 *
 * @param pos          position in template space, relative to the template's base centre
 * @param paletteIndex index into {@link StructureTemplate#palette()}
 */
public record TemplateBlock(BlockPos pos, int paletteIndex) {

    public TemplateBlock {
        if (paletteIndex < 0) {
            throw new IllegalArgumentException("paletteIndex must not be negative, was " + paletteIndex);
        }
    }
}