-- Adds Set of Semesters as attribute of Fulluser

--
-- Name: fulluser_semesters; Type: TABLE; Schema: public; Owner: tepid
--

CREATE TABLE public.fulluser_semesters (
                                           fulluser__id character varying(36) NOT NULL,
                                           season integer,
                                           year integer NOT NULL
);


ALTER TABLE public.fulluser_semesters OWNER TO tepid;

--
-- Name: fulluser_semesters fk7boa81tl3qqsoh85n6raknp63; Type: FK CONSTRAINT; Schema: public; Owner: tepid
--

ALTER TABLE ONLY public.fulluser_semesters
    ADD CONSTRAINT fk7boa81tl3qqsoh85n6raknp63 FOREIGN KEY (fulluser__id) REFERENCES public.fulluser(_id);
