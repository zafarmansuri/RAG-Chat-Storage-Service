package ai.xdigit.ragchatstorage.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    @Test
    void defaultAllowedOriginsIsSingleLocalhost() {
        CorsProperties props = new CorsProperties();
        assertThat(props.getAllowedOrigins()).isEqualTo("http://localhost:3000");
        assertThat(props.getAllowedOriginsList()).containsExactly("http://localhost:3000");
    }

    @Test
    void getAllowedOriginsListSplitsOnComma() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins("http://a.com, http://b.com, http://c.com");

        List<String> list = props.getAllowedOriginsList();
        assertThat(list).containsExactly("http://a.com", "http://b.com", "http://c.com");
    }

    @Test
    void getAllowedOriginsListFiltersBlankEntries() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins(",http://a.com,,");

        assertThat(props.getAllowedOriginsList()).containsExactly("http://a.com");
    }

    @Test
    void setAllowedOriginsUpdatesRawValue() {
        CorsProperties props = new CorsProperties();
        props.setAllowedOrigins("http://example.com");
        assertThat(props.getAllowedOrigins()).isEqualTo("http://example.com");
    }
}
