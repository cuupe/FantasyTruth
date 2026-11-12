import java.io.File;
import java.nio.file.Path;
import net.neoforged.srgutils.IMappingFile;

class LocalMappings {
    public static void main(String[] args) throws Exception {
        var srg = IMappingFile.load(new File(args[0]));
        var official = IMappingFile.load(new File(args[1]));
        var srgToOfficial = srg.reverse().chain(official.reverse());
        srgToOfficial.write(Path.of(args[2]), IMappingFile.Format.TSRG2, false);
        srgToOfficial.write(Path.of(args[3]), IMappingFile.Format.TSRG2, true);
    }
}
