import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

@ProtoDoc("@Indexed")
public class Author {

   @ProtoDoc("@Field(index=Index.YES, analyze = Analyze.YES, store = Store.NO)")
   @ProtoField(number = 1)
   final String name;

   @ProtoDoc("@Field(index=Index.YES, analyze = Analyze.YES, store = Store.NO)")
   @ProtoField(number = 2)
   final String surname;

   @ProtoFactory
   Author(String name, String surname) {
      this.name = name;
      this.surname = surname;
   }
   // public Getter methods omitted for brevity
}