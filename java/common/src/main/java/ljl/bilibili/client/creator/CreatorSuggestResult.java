package ljl.bilibili.client.creator;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class CreatorSuggestResult {
    private List<String> titles = new ArrayList<>();
    private List<String> intros = new ArrayList<>();
    private List<CreatorReferenceItem> references = new ArrayList<>();
}
