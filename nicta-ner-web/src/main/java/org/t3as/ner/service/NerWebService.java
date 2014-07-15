/*
 * #%L
 * NICTA t3as NER web service
 * %%
 * Copyright (C) 2014 NICTA
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package org.t3as.ner.service;

import org.t3as.ner.NamedEntityAnalyser;
import org.t3as.ner.NerResultSet;
import org.t3as.ner.resource.Configuration;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;

@Path("v1.0/ner")
public class NerWebService {

    static final Configuration conf;

    static {
        try { conf = new Configuration(); }
        catch (final IOException e) { throw new RuntimeException("Could not load configuraton.", e); }
    }

    @GET
    public InputStream getDoc() throws IOException {
        //noinspection ConstantConditions
        return getClass().getClassLoader().getResource("NerWebService_help.txt").openStream();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public NerResultSet ner(final String text) throws IOException {
        final NamedEntityAnalyser nea = new NamedEntityAnalyser(conf);
        final NerResultSet resultSet = nea.process(URLDecoder.decode(text, "UTF-8"));
        System.out.println(resultSet);
        return resultSet;
    }
}